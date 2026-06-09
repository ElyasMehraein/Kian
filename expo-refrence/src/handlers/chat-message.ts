import { buildDeliveredReceipt } from '@/builders';
import { giftUnwrap, giftWrap } from '@/crypto';
import { conversationsRepo, keysRepo, messagesRepo } from '@/db/repos';
import { handleDelivered } from '@/handlers/delivered';
import { handleProductRequestDeclined } from '@/handlers/product-request-declined';
import { handleProductRequestFulfilled } from '@/handlers/product-request-fulfilled';
import { handleProductRequestWaitingMint } from '@/handlers/product-request-waiting-mint';
import { handleTokenTransferFulfilled } from '@/handlers/token-transfer-fulfilled';
import { handleTokenTransferRejected } from '@/handlers/token-transfer-rejected';
import { handleTokenTransferWaitingIssuer } from '@/handlers/token-transfer-waiting-issuer';
import { handleRead } from '@/handlers/read';
import { chatService } from '@/services/chat';
import { publishOrQueue } from '@/services/chat-transport';
import { getPublishRelayUrls } from '@/services/dm-relays';
import { messageEvents } from '@/services/message-events';
import { EVENT_KIND } from '@/types';
import type { ChatMessage, MessageType, NostrEvent, UnsignedEvent } from '@/types';

type ChatPayload = {
  content?: string;
  type?: MessageType;
};

const CHAT_MESSAGE_TYPE_TAG = 'kian_type';
const MESSAGE_TYPES: MessageType[] = [
  'text',
  'image',
  'product_request',
  'product_send',
  'token_transfer',
  'conversation_delete',
];
function isMessageType(value: unknown): value is MessageType {
  return typeof value === 'string' && MESSAGE_TYPES.includes(value as MessageType);
}

function getTaggedMessageType(event: UnsignedEvent): MessageType | null {
  const taggedType = event.tags.find(([name]) => name === CHAT_MESSAGE_TYPE_TAG)?.[1];
  return isMessageType(taggedType) ? taggedType : null;
}

function parseChatPayload(event: UnsignedEvent): { content: string; type: MessageType } {
  const taggedType = getTaggedMessageType(event);

  if (taggedType) {
    return {
      content: event.content,
      type: taggedType,
    };
  }

  try {
    const payload = JSON.parse(event.content) as ChatPayload;

    if (typeof payload.content === 'string') {
      return {
        content: payload.content,
        type: isMessageType(payload.type) ? payload.type : 'text',
      };
    }
  } catch {}

  return {
    content: event.content,
    type: 'text',
  };
}

function resolveConversationPubkey(event: UnsignedEvent, selfPubkey: string): string {
  if (event.pubkey !== selfPubkey) {
    return event.pubkey;
  }

  return event.tags.find(([name, value]) => name === 'p' && value && value !== selfPubkey)?.[1] ?? selfPubkey;
}

function buildChatMessage(
  event: UnsignedEvent,
  eventId: string,
  selfPubkey: string,
  transportEventId: string,
): ChatMessage {
  const parsed = parseChatPayload(event);
  const conversationPubkey = resolveConversationPubkey(event, selfPubkey);

  return {
    id: eventId,
    conversation_pubkey: conversationPubkey,
    sender: event.pubkey,
    content: parsed.content,
    message_type: parsed.type,
    created_at: event.created_at,
    status: 'sent',
    request_status: parsed.type === 'product_request' ? 'open' : undefined,
    transfer_counterparty: parsed.type === 'token_transfer' ? conversationPubkey : undefined,
    transport_event_id: transportEventId,
  };
}

async function sendDeliveredReceipt(
  messageId: string,
  peerPubkey: string,
  pubkey: string,
  privkey: string,
): Promise<void> {
  const peerRelayUrls = getPublishRelayUrls(peerPubkey);
  const wrapped = giftWrap(
    {
      ...buildDeliveredReceipt(messageId),
      pubkey,
      tags: [['e', messageId], ['p', peerPubkey]],
    },
    privkey,
    peerPubkey,
  );

  await publishOrQueue(wrapped.wrap, peerRelayUrls);
}

export async function handleChatMessage(event: NostrEvent): Promise<void> {
  const [pubkey, privkey] = await Promise.all([
    keysRepo.getPublicKey(),
    keysRepo.getPrivateKey(),
  ]);

  if (!pubkey || !privkey) {
    throw new Error('Private key is unavailable');
  }

  const unwrapped = giftUnwrap(event, privkey);

  if (!unwrapped) {
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.RECEIPT_DELIVERED) {
    await handleDelivered(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.RECEIPT_READ) {
    await handleRead(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.PRODUCT_REQUEST_DECLINED) {
    await handleProductRequestDeclined(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.PRODUCT_REQUEST_FULFILLED) {
    await handleProductRequestFulfilled(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.PRODUCT_REQUEST_WAITING_MINT) {
    await handleProductRequestWaitingMint(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.TOKEN_TRANSFER_WAITING_ISSUER) {
    await handleTokenTransferWaitingIssuer(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.TOKEN_TRANSFER_FULFILLED) {
    await handleTokenTransferFulfilled(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind === EVENT_KIND.TOKEN_TRANSFER_REJECTED) {
    await handleTokenTransferRejected(unwrapped.event);
    return;
  }

  if (unwrapped.event.kind !== EVENT_KIND.DIRECT_MESSAGE) {
    return;
  }

  const message = buildChatMessage(unwrapped.event, unwrapped.eventId, pubkey, event.id);
  const isSelfMessage = message.sender === pubkey;
  const deletedAt = await conversationsRepo.getDeletedAt(message.conversation_pubkey);

  if (deletedAt != null && message.created_at <= deletedAt) {
    return;
  }

  if (message.message_type === 'conversation_delete') {
    await chatService.applyConversationDelete(message.conversation_pubkey, message.created_at);
    return;
  }

  const existingConversation = await messagesRepo.getConversation(message.conversation_pubkey);
  const existingMessage = existingConversation.find((entry) => entry.id === message.id);

  await messagesRepo.insert(message);
  await conversationsRepo.upsert(message.conversation_pubkey);
  await conversationsRepo.updateLastMessage(
    message.conversation_pubkey,
    message.content,
    message.created_at,
  );

  if (!isSelfMessage && !existingMessage) {
    await conversationsRepo.incrementUnread(message.conversation_pubkey);
    await sendDeliveredReceipt(message.id, message.sender, pubkey, privkey);
  }

  messageEvents.emit();
}
