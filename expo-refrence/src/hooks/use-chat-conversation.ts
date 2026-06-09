import { useEffect, useState } from 'react';

import { initDatabase } from '@/db';
import { conversationsRepo, messagesRepo } from '@/db/repos';
import { messageEvents } from '@/services/message-events';
import type { ChatMessage } from '@/types';

export function useChatConversation(peer: string): ChatMessage[] {
  const [messages, setMessages] = useState<ChatMessage[]>([]);

  useEffect(() => {
    if (!peer) {
      setMessages([]);
      return;
    }

    let isMounted = true;

    async function loadMessages(): Promise<void> {
      await initDatabase();
      const [deletedAt, nextMessages] = await Promise.all([
        conversationsRepo.getDeletedAt(peer),
        messagesRepo.getConversation(peer),
      ]);
      const visibleMessages = nextMessages.filter(
        (message) => message.message_type !== 'conversation_delete'
          && (deletedAt == null || message.created_at > deletedAt),
      );

      if (isMounted) {
        setMessages(visibleMessages);
      }

      await conversationsRepo.resetUnread(peer);
    }

    void loadMessages();

    const unsubscribe = messageEvents.subscribe(() => {
      void loadMessages();
    });

    return () => {
      isMounted = false;
      unsubscribe();
    };
  }, [peer]);

  return messages;
}
