import { signEvent } from '@/crypto';
import { keysRepo, messagesRepo, utxoRepo } from '@/db/repos';
import { RelayPool } from '@/nostr/relayPool';
import { chatService } from '@/services/chat';
import { messageEvents } from '@/services/message-events';
import { EVENT_KIND } from '@/types';
import type { NostrEvent, TokenUtxo, UnsignedEvent } from '@/types';

type TransferRequestContent = {
  utxo_id?: string;
  utxoId?: string;
  amount?: number;
  to?: string;
  recipient?: string;
  owner?: string;
};

const relayPool = new RelayPool();
let isConnected = false;

function parseTransferRequestContent(content: string): TransferRequestContent {
  return JSON.parse(content) as TransferRequestContent;
}

function getUtxoId(event: NostrEvent, content: TransferRequestContent): string {
  const utxoId = content.utxo_id ?? content.utxoId ?? event.tags.find(([name]) => name === 'e')?.[1];

  if (!utxoId) {
    throw new Error('Missing transfer request utxo id');
  }

  return utxoId;
}

function getRecipient(event: NostrEvent, content: TransferRequestContent): string {
  const recipient = content.to ?? content.recipient ?? content.owner;

  if (!recipient) {
    throw new Error('Missing transfer request recipient');
  }

  return recipient;
}

function getAmount(content: TransferRequestContent, utxo: TokenUtxo): number {
  return content.amount ?? utxo.amount;
}

function buildTokenUtxoEvent(
  producerPubkey: string,
  owner: string,
  amount: number,
  assetRef: string,
  prevUtxoId: string,
): UnsignedEvent {
  return {
    pubkey: producerPubkey,
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.TOKEN_UTXO,
    tags: [
      ['a', assetRef],
      ['p', owner],
      ['e', prevUtxoId],
    ],
    content: JSON.stringify({
      asset_ref: assetRef,
      owner,
      amount,
      prev_utxo_id: prevUtxoId,
    }),
  };
}

async function ensureRelayConnected(): Promise<void> {
  if (isConnected) {
    return;
  }

  await relayPool.connect();
  isConnected = true;
}

async function mintAndPublish(
  producerPubkey: string,
  producerPrivkey: string,
  owner: string,
  amount: number,
  assetRef: string,
  prevUtxoId: string,
): Promise<void> {
  const event = signEvent(
    buildTokenUtxoEvent(producerPubkey, owner, amount, assetRef, prevUtxoId),
    producerPrivkey,
  );

  await utxoRepo.insert({
    utxo_id: event.id,
    asset_ref: assetRef,
    producer: producerPubkey,
    owner,
    amount,
    prev_utxo_id: prevUtxoId,
    created_at: event.created_at,
    spent: false,
  });

  relayPool.publish(event);
}

async function findTransferMessage(sourceUtxoId: string, sender: string, recipient: string) {
  const messages = await messagesRepo.listTokenTransfersByUtxo(sourceUtxoId);

  return messages.find((message) => {
    if (message.request_status !== 'waiting_mint') {
      return false;
    }

    try {
      const parsed = JSON.parse(message.content) as { recipient?: string; sender?: string };
      return parsed.recipient === recipient && parsed.sender === sender;
    } catch {
      return false;
    }
  }) ?? null;
}

async function rejectCompetingTransfers(sourceUtxoId: string, approvedMessageId: string, recipient: string): Promise<void> {
  const transfers = await messagesRepo.listTokenTransfersByUtxo(sourceUtxoId);

  for (const transfer of transfers) {
    if (transfer.id === approvedMessageId || transfer.request_status !== 'waiting_mint') {
      continue;
    }

    await messagesRepo.updateRequestMetadata(transfer.id, {
      request_status: 'rejected',
      transfer_decision: 'rejected',
      transfer_recipient: recipient,
    });

    const peer = transfer.conversation_pubkey;
    await chatService.rejectTokenTransfer(peer, transfer.id);
  }
}

export async function handleTransferRequest(event: NostrEvent): Promise<void> {
  const content = parseTransferRequestContent(event.content);
  const utxoId = getUtxoId(event, content);
  const utxo = await utxoRepo.get(utxoId);

  if (!utxo) {
    throw new Error('Transfer request UTXO not found');
  }

  if (utxo.spent) {
    const rejectedTransfer = await findTransferMessage(utxo.utxo_id, event.pubkey, getRecipient(event, content));

    if (rejectedTransfer) {
      await messagesRepo.updateRequestMetadata(rejectedTransfer.id, {
        request_status: 'rejected',
        transfer_decision: 'rejected',
      });
      await chatService.rejectTokenTransfer(rejectedTransfer.conversation_pubkey, rejectedTransfer.id);
    }

    throw new Error('Transfer request UTXO already spent');
  }

  if (utxo.owner !== event.pubkey) {
    throw new Error('Transfer request sender does not own UTXO');
  }

  const amount = getAmount(content, utxo);

  if (!Number.isInteger(amount) || amount <= 0 || amount > utxo.amount) {
    throw new Error('Invalid transfer request amount');
  }

  const [producerPubkey, producerPrivkey] = await Promise.all([
    keysRepo.getPublicKey(),
    keysRepo.getPrivateKey(),
  ]);

  if (!producerPubkey || !producerPrivkey) {
    throw new Error('Producer keys are unavailable');
  }

  if (producerPubkey !== utxo.producer) {
    throw new Error('Current keys do not match UTXO producer');
  }

  const recipient = getRecipient(event, content);
  const changeAmount = utxo.amount - amount;

  await utxoRepo.markSpent(utxo.utxo_id);
  await ensureRelayConnected();

  await mintAndPublish(
    producerPubkey,
    producerPrivkey,
    recipient,
    amount,
    utxo.asset_ref,
    utxo.utxo_id,
  );

  if (changeAmount > 0) {
    await mintAndPublish(
      producerPubkey,
      producerPrivkey,
      event.pubkey,
      changeAmount,
      utxo.asset_ref,
      utxo.utxo_id,
    );
  }

  const approvedTransfer = await findTransferMessage(utxo.utxo_id, event.pubkey, recipient);

  if (approvedTransfer) {
    await messagesRepo.resolveTokenTransfersForUtxo(utxo.utxo_id, approvedTransfer.id, recipient);
    await chatService.fulfillTokenTransfer(approvedTransfer.conversation_pubkey, approvedTransfer.id);
    await rejectCompetingTransfers(utxo.utxo_id, approvedTransfer.id, recipient);
  }

  messageEvents.emit();
}

