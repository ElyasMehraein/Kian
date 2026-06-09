import { messagesRepo } from '@/db/repos';
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
    throw new Error('Missing token transfer message id');
  }

  return fromContent;
}

export async function handleTokenTransferWaitingIssuer(event: Pick<UnsignedEvent, 'tags' | 'content'>): Promise<void> {
  const messageId = getMessageId(event);

  await messagesRepo.updateTokenTransferStatus(messageId, 'waiting_mint');
  messageEvents.emit();
}
