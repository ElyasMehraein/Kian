import * as SMS from 'expo-sms';

import {
  buildChatMessage,
  buildDeliveredReceipt,
  buildProductRequestDeclined,
  buildProductRequestFulfilled,
  buildProductRequestWaitingMint,
  buildReadReceipt,
  buildTokenTransferFulfilled,
  buildTokenTransferRejected,
  buildTokenTransferWaitingIssuer,
} from '@/builders';
import { giftWrap, signEvent } from '@/crypto';
import { conversationsRepo, keysRepo, messagesRepo, receiptsRepo } from '@/db/repos';
import { getPublishRelayUrls, waitForInboxRelayUrls } from '@/services/dm-relays';
import { messageEvents } from '@/services/message-events';
import { subscriptionService } from '@/services/subscriptions';
import {
  createOfflineSmsPayload,
  decodeOfflinePayload,
  deleteLocalRelayCopies,
  encodeOfflinePayload,
  publishOrQueue,
} from '@/services/chat-transport';
import { EVENT_KIND } from '@/types';
import type { ChatMessage, MessageType, NostrEvent, ProductRequest, ProductSend, TokenTransfer } from '@/types';

async function getSenderKeys(): Promise<{ pubkey: string; privkey: string }> {
  const [pubkey, privkey] = await Promise.all([
    keysRepo.getPublicKey(),
    keysRepo.getPrivateKey(),
  ]);

  if (!pubkey || !privkey) {
    throw new Error('Sender keys are unavailable');
  }

  return { pubkey, privkey };
}

function withSenderPubkey(
  message: ReturnType<typeof buildChatMessage>,
  pubkey: string,
): ReturnType<typeof buildChatMessage> {
  return { ...message, pubkey };
}

function toLocalMessage(
  event: { id: string; created_at: number; pubkey: string },
  peer: string,
  content: string,
  type: MessageType,
): ChatMessage {
  return {
    id: event.id,
    conversation_pubkey: peer,
    sender: event.pubkey,
    content,
    message_type: type,
    created_at: event.created_at,
    status: 'sending',
    request_status: type === 'product_request' ? 'open' : undefined,
  };
}

function buildWrappedCopies(
  message: ReturnType<typeof buildChatMessage>,
  senderPrivkey: string,
  peerPubkey: string,
) {
  return {
    peer: giftWrap(message, senderPrivkey, peerPubkey),
    self: giftWrap(message, senderPrivkey, message.pubkey),
  };
}

async function sendMessage(peer: string, content: string, type: MessageType): Promise<string> {
  const { pubkey, privkey } = await getSenderKeys();
  const unsigned = withSenderPubkey(buildChatMessage(peer, content, type), pubkey);
  const signed = signEvent(unsigned, privkey);
  const wrapped = buildWrappedCopies(unsigned, privkey, peer);
  subscriptionService.watchDmRelayList(peer);
  const peerRelayUrls = await waitForInboxRelayUrls(peer);
  const selfRelayUrls = getPublishRelayUrls(pubkey);
  const localMessage = {
    ...toLocalMessage(signed, peer, content, type),
    transport_event_id: wrapped.peer.wrap.id,
    transport_privkey: wrapped.peer.ephemeralPrivkey,
  } satisfies ChatMessage;

  await messagesRepo.insert(localMessage);
  await conversationsRepo.upsert(peer);
  await conversationsRepo.updateLastMessage(peer, content, signed.created_at);
  messageEvents.emit();
  const [publishedPeer] = await Promise.all([
    publishOrQueue(wrapped.peer.wrap, peerRelayUrls, {
      scope: 'private_message_peer_copy',
      peerPubkey: peer,
    }),
    publishOrQueue(wrapped.self.wrap, selfRelayUrls, {
      scope: 'private_message_self_copy',
      peerPubkey: peer,
    }),
  ]);

  if (publishedPeer) {
    await messagesRepo.updateStatus(signed.id, 'sent');
  }

  messageEvents.emit();
  return signed.id;
}

async function publishReadReceipt(messageId: string, peer: string): Promise<void> {
  if (await receiptsRepo.hasRead(messageId)) {
    return;
  }

  await publishPrivateReceipt(buildReadReceipt(messageId), peer);
  await receiptsRepo.addRead(messageId);
}

async function publishDeliveredReceipt(messageId: string, peer: string): Promise<void> {
  if (await receiptsRepo.hasDelivered(messageId)) {
    return;
  }

  await publishPrivateReceipt(buildDeliveredReceipt(messageId), peer);
  await receiptsRepo.addDelivered(messageId);
}

async function publishPrivateReceipt(
  receipt: ReturnType<typeof buildReadReceipt>,
  peer: string,
): Promise<void> {
  const { pubkey, privkey } = await getSenderKeys();
  const peerRelayUrls = getPublishRelayUrls(peer);
  const wrapped = giftWrap(
    {
      ...receipt,
      pubkey,
      tags: [...receipt.tags, ['p', peer]],
    },
    privkey,
    peer,
  );

  await publishOrQueue(wrapped.wrap, peerRelayUrls, {
    scope: 'private_receipt',
    peerPubkey: peer,
  });
}

export const chatService = {
  async sendText(peer: string, text: string): Promise<void> {
    await sendMessage(peer, text, 'text');
  },

  async sendProduct(peer: string, productSend: ProductSend): Promise<void> {
    await sendMessage(peer, JSON.stringify(productSend), 'product_send');
  },

  async sendTokenTransfer(peer: string, transfer: TokenTransfer): Promise<string> {
    const localId = await sendMessage(peer, JSON.stringify(transfer), 'token_transfer');
    await messagesRepo.updateRequestMetadata(localId, {
      transfer_counterparty: transfer.sender === peer ? transfer.recipient : peer,
      transfer_recipient: transfer.recipient,
      transfer_origin_message_id: localId,
    });
    return localId;
  },

  async requestProduct(peer: string, req: ProductRequest): Promise<void> {
    await sendMessage(peer, JSON.stringify(req), 'product_request');
  },

  async declineProductRequest(peer: string, messageId: string): Promise<void> {
    await messagesRepo.updateRequestStatus(messageId, 'declined');
    messageEvents.emit();
    await publishPrivateReceipt(buildProductRequestDeclined(messageId), peer);
  },

  async fulfillProductRequest(peer: string, messageId: string): Promise<void> {
    await messagesRepo.updateRequestStatus(messageId, 'fulfilled');
    messageEvents.emit();
    await publishPrivateReceipt(buildProductRequestFulfilled(messageId), peer);
  },

  async markProductRequestWaitingForMint(peer: string, messageId: string, transferUtxoId: string): Promise<void> {
    await messagesRepo.updateRequestMetadata(messageId, {
      request_status: 'waiting_mint',
      request_transfer_utxo_id: transferUtxoId,
    });
    messageEvents.emit();
    await publishPrivateReceipt(buildProductRequestWaitingMint(messageId), peer);
  },

  async markTokenTransferWaitingForIssuer(peer: string, messageId: string, transferUtxoId: string): Promise<void> {
    await messagesRepo.updateRequestMetadata(messageId, {
      request_status: 'waiting_mint',
      request_transfer_utxo_id: transferUtxoId,
      transfer_counterparty: peer,
      transfer_origin_message_id: messageId,
    });
    messageEvents.emit();
    await publishPrivateReceipt(buildTokenTransferWaitingIssuer(messageId), peer);
  },

  async fulfillTokenTransfer(peer: string, messageId: string): Promise<void> {
    await messagesRepo.updateRequestStatus(messageId, 'fulfilled');
    messageEvents.emit();
    await publishPrivateReceipt(buildTokenTransferFulfilled(messageId), peer);
  },

  async rejectTokenTransfer(peer: string, messageId: string): Promise<void> {
    await messagesRepo.updateRequestStatus(messageId, 'rejected');
    messageEvents.emit();
    await publishPrivateReceipt(buildTokenTransferRejected(messageId), peer);
  },

  async sendConversationDelete(peer: string): Promise<void> {
    await sendMessage(peer, JSON.stringify({ scope: 'conversation' }), 'conversation_delete');
  },

  async applyConversationDelete(peer: string, deletedAt?: number): Promise<void> {
    const resolvedDeletedAt =
      deletedAt
      ?? await messagesRepo.getLatestConversationDeleteAt(peer)
      ?? Math.floor(Date.now() / 1000);

    await deleteLocalRelayCopies(await messagesRepo.listConversationTransport(peer));
    await conversationsRepo.markDeleted(peer, resolvedDeletedAt);
    messageEvents.emit();
  },

  async deleteConversation(peer: string): Promise<void> {
    await this.sendConversationDelete(peer);
    await this.applyConversationDelete(peer);
  },

  async markConversationRead(peer: string, messages: ChatMessage[]): Promise<void> {
    for (const message of messages.filter((message) => message.sender === peer && message.status !== 'read')) {
      if (message.status === 'sent') {
        await publishDeliveredReceipt(message.id, peer);
      }

      await publishReadReceipt(message.id, peer);
    }
  },

  isSupportedPrivateEventKind(kind: number): boolean {
    return ([
      EVENT_KIND.DIRECT_MESSAGE,
      EVENT_KIND.RECEIPT_DELIVERED,
      EVENT_KIND.RECEIPT_READ,
      EVENT_KIND.PRODUCT_REQUEST_DECLINED,
      EVENT_KIND.PRODUCT_REQUEST_FULFILLED,
      EVENT_KIND.PRODUCT_REQUEST_WAITING_MINT,
      EVENT_KIND.TOKEN_TRANSFER_REJECTED,
      EVENT_KIND.TOKEN_TRANSFER_FULFILLED,
      EVENT_KIND.TOKEN_TRANSFER_WAITING_ISSUER,
    ] as number[]).includes(kind);
  },

  encodeOfflinePayload,

  async sendOfflinePayloadViaSms(eventId: string): Promise<'sent' | 'cancelled' | 'unknown'> {
    const payload = await encodeOfflinePayload(eventId);

    if (!payload) {
      throw new Error('Offline payload unavailable');
    }

    if (!(await SMS.isAvailableAsync())) {
      throw new Error('SMS is unavailable on this device');
    }

    const response = await SMS.sendSMSAsync([], createOfflineSmsPayload(payload));
    return response.result;
  },

  decodeOfflinePayload(payload: string): NostrEvent | null {
    return decodeOfflinePayload(payload);
  },
};
