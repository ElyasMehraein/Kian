import { messagesRepo, receiptsRepo } from '@/db/repos';
import { messageEvents } from '@/services/message-events';
import type { UnsignedEvent } from '@/types';

type ReceiptContent = {
  message_id?: string;
  msgId?: string;
};

function parseReceiptContent(content: string): ReceiptContent | null {
  try {
    return JSON.parse(content) as ReceiptContent;
  } catch {
    return null;
  }
}

function getMessageId(event: Pick<UnsignedEvent, 'tags' | 'content'>): string {
  const fromTag = event.tags.find(([name]) => name === 'e')?.[1];

  if (fromTag) {
    return fromTag;
  }

  const parsed = parseReceiptContent(event.content);
  const fromContent = parsed?.message_id ?? parsed?.msgId ?? event.content;

  if (!fromContent) {
    throw new Error('Missing delivered receipt message id');
  }

  return fromContent;
}

export async function handleDelivered(event: Pick<UnsignedEvent, 'tags' | 'content'>): Promise<void> {
  const messageId = getMessageId(event);

  await receiptsRepo.addDelivered(messageId);
  await messagesRepo.updateStatus(messageId, 'delivered');
  messageEvents.emit();
}
