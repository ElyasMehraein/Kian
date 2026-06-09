import { messagesRepo, utxoRepo } from '@/db/repos';
import { chatService } from '@/services/chat';
import { messageEvents } from '@/services/message-events';
import type { NostrEvent, ProductRequest, TokenTransfer, TokenUtxo } from '@/types';

type TokenUtxoContent = Partial<
  Pick<TokenUtxo, 'asset_ref' | 'owner' | 'amount' | 'prev_utxo_id'>
> & {
  asset_id?: string;
};

function parseTokenUtxoContent(content: string): TokenUtxoContent {
  return JSON.parse(content) as TokenUtxoContent;
}

function getTagValue(event: NostrEvent, tagName: string): string | undefined {
  return event.tags.find(([name]) => name === tagName)?.[1];
}

function getUtxoId(event: NostrEvent): string {
  const utxoId = getTagValue(event, 'd') ?? event.id;

  return utxoId;
}

function getOwner(event: NostrEvent, content: TokenUtxoContent): string {
  return content.owner ?? getTagValue(event, 'p') ?? event.pubkey;
}

function getAssetRef(event: NostrEvent, content: TokenUtxoContent): string {
  return content.asset_ref ?? content.asset_id ?? getTagValue(event, 'a') ?? '';
}

function parseAssetRef(assetRef: string): { producer: string; assetId: string } | null {
  const [kind, producer, assetId] = assetRef.split(':', 3);

  return kind === '35001' && producer && assetId ? { producer, assetId } : null;
}


function parseTokenTransfer(content: string): TokenTransfer | null {
  try {
    const parsed = JSON.parse(content) as TokenTransfer;

    return typeof parsed.utxo_id === 'string'
      && typeof parsed.asset_ref === 'string'
      && Number.isInteger(parsed.amount)
      && typeof parsed.sender === 'string'
      && typeof parsed.recipient === 'string'
      ? parsed
      : null;
  } catch {
    return null;
  }
}

function parseProductRequest(content: string): ProductRequest | null {
  try {
    const parsed = JSON.parse(content) as ProductRequest;

    return typeof parsed.product_id === 'string' && Number.isInteger(parsed.quantity)
      ? parsed
      : null;
  } catch {
    return null;
  }
}

export async function handleTokenUTXO(event: NostrEvent): Promise<void> {
  const content = parseTokenUtxoContent(event.content);
  const owner = getOwner(event, content);
  const assetRef = getAssetRef(event, content);
  const amount = content.amount ?? 0;
  const prevUtxoId = content.prev_utxo_id;

  await utxoRepo.insert({
    utxo_id: getUtxoId(event),
    asset_ref: assetRef,
    producer: event.pubkey,
    owner,
    amount,
    prev_utxo_id: prevUtxoId,
    created_at: event.created_at,
    spent: false,
  });
  messageEvents.emit();

  if (!prevUtxoId) {
    return;
  }

  const parsedAssetRef = parseAssetRef(assetRef);

  if (!parsedAssetRef) {
    return;
  }

  const productRequests = await messagesRepo.listProductRequests();
  const matchingRequest = productRequests.find((message) => {
    const request = parseProductRequest(message.content);

    return (
      message.request_status === 'waiting_mint'
      && message.request_transfer_utxo_id === prevUtxoId
      && request?.product_id === parsedAssetRef.assetId
      && request.quantity === amount
      && message.conversation_pubkey === owner
    );
  });

  if (matchingRequest) {
    await chatService.fulfillProductRequest(matchingRequest.conversation_pubkey, matchingRequest.id);
  }

  const matchingTransfers = await messagesRepo.listTokenTransfersByUtxo(prevUtxoId);
  let approvedTransferId: string | null = null;

  for (const matchingTransfer of matchingTransfers) {
    const transfer = matchingTransfer.message_type === 'token_transfer' ? parseTokenTransfer(matchingTransfer.content) : null;

    if (matchingTransfer.request_status !== 'waiting_mint'
      || transfer?.asset_ref !== assetRef
      || transfer.amount !== amount
      || transfer.recipient !== owner) {
      continue;
    }

    approvedTransferId = matchingTransfer.id;
    await chatService.fulfillTokenTransfer(matchingTransfer.conversation_pubkey, matchingTransfer.id);
  }

  if (approvedTransferId) {
    await messagesRepo.resolveTokenTransfersForUtxo(prevUtxoId, approvedTransferId, owner);
  }
}


export async function resolveTokenTransferByUtxo(prevUtxoId: string, owner: string, amount: number, assetRef: string): Promise<void> {
  const messages = await messagesRepo.getConversation(owner);
  const matchingTransfer = messages.find((message) => {
    const transfer = message.message_type === 'token_transfer' ? parseTokenTransfer(message.content) : null;

    return message.request_status === 'waiting_mint'
      && message.request_transfer_utxo_id === prevUtxoId
      && transfer?.asset_ref === assetRef
      && transfer.amount === amount
      && transfer.recipient === owner;
  });

  if (!matchingTransfer) {
    return;
  }

  await chatService.fulfillTokenTransfer(matchingTransfer.conversation_pubkey, matchingTransfer.id);
}
