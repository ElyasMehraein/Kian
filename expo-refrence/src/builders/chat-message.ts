import { EVENT_KIND } from '@/types';
import type { MessageType, UnsignedEvent } from '@/types';

const CHAT_MESSAGE_TYPE_TAG = 'kian_type';

function buildChatTags(peer: string, type: MessageType): string[][] {
  return type === 'text'
    ? [['p', peer]]
    : [['p', peer], [CHAT_MESSAGE_TYPE_TAG, type]];
}

export function buildChatMessage(
  peer: string,
  content: string,
  type: MessageType,
): UnsignedEvent {
  return {
    pubkey: '',
    created_at: Math.floor(Date.now() / 1000),
    kind: EVENT_KIND.DIRECT_MESSAGE,
    tags: buildChatTags(peer, type),
    content,
  };
}
