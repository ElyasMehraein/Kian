import { persistState, type WebDbState, type WebQueryResult } from './state';

export function handleMessageAndConversationQueries(
  normalized: string,
  state: WebDbState,
  params: unknown[],
): WebQueryResult | null {
  if (normalized.includes('insert or replace into messages')) {
    const [id, conversationPubkey, sender, content, messageType, createdAt, status, rawJson] = params as [
      string,
      string,
      string,
      string,
      string,
      number,
      string,
      string | null,
    ];
    const existingIndex = state.messages.findIndex((message) => message.id === id);
    const nextMessage = { id, conversation_pubkey: conversationPubkey, sender, content, message_type: messageType, created_at: createdAt, status, raw_json: rawJson };

    if (existingIndex >= 0) {
      state.messages[existingIndex] = nextMessage;
    } else {
      state.messages.push(nextMessage);
    }

    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select * from messages where conversation_pubkey = ?')) {
    const [peer] = params as [string];
    const rows = [...state.messages]
      .filter((message) => message.conversation_pubkey === peer)
      .sort((left, right) => left.created_at - right.created_at);

    return { rows };
  }

  if (normalized.includes("select * from messages where message_type = 'product_request' order by created_at asc")) {
    const rows = [...state.messages]
      .filter((message) => message.message_type === 'product_request')
      .sort((left, right) => left.created_at - right.created_at);

    return { rows };
  }


  if (normalized.includes("select * from messages where message_type = 'token_transfer' order by created_at asc")) {
    const rows = [...state.messages]
      .filter((message) => message.message_type === 'token_transfer')
      .sort((left, right) => left.created_at - right.created_at);

    return { rows };
  }

  if (normalized.includes('delete from messages where conversation_pubkey = ? and created_at <= ?')) {
    const [peer, createdAt] = params as [string, number];
    state.messages = state.messages.filter(
      (message) => !(message.conversation_pubkey === peer && message.created_at <= createdAt),
    );
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('delete from messages where conversation_pubkey = ?')) {
    const [peer] = params as [string];
    state.messages = state.messages.filter((message) => message.conversation_pubkey !== peer);
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes("select created_at from messages where conversation_pubkey = ? and message_type = 'conversation_delete' order by created_at desc limit 1")) {
    const [peer] = params as [string];
    const rows = [...state.messages]
      .filter((message) => message.conversation_pubkey === peer && message.message_type === 'conversation_delete')
      .sort((left, right) => right.created_at - left.created_at)
      .slice(0, 1)
      .map((message) => ({ created_at: message.created_at }));

    return { rows };
  }

  if (normalized.includes('select raw_json from messages where id = ? limit 1')) {
    const [id] = params as [string];
    const message = state.messages.find((entry) => entry.id === id);
    return { rows: message ? [{ raw_json: message.raw_json }] : [] };
  }

  if (normalized.includes('select status, raw_json from messages where id = ? limit 1')) {
    const [id] = params as [string];
    const message = state.messages.find((entry) => entry.id === id);
    return { rows: message ? [{ status: message.status, raw_json: message.raw_json }] : [] };
  }

  if (normalized.includes('update messages set status = ?, raw_json = coalesce(?, raw_json) where id = ?')) {
    const [status, nextRawJson, id] = params as [string, string | null, string];
    const existingIndex = state.messages.findIndex((message) => message.id === id);

    if (existingIndex >= 0) {
      const existing = state.messages[existingIndex];
      state.messages[existingIndex] = { ...existing, status, raw_json: nextRawJson ?? existing.raw_json };
      persistState(state);
    }

    return { rows: [] };
  }

  if (normalized.includes('update messages set status = ?, raw_json = ? where id = ?')) {
    const [status, nextRawJson, id] = params as [string, string, string];
    const existingIndex = state.messages.findIndex((message) => message.id === id);

    if (existingIndex >= 0) {
      const existing = state.messages[existingIndex];
      state.messages[existingIndex] = { ...existing, status, raw_json: nextRawJson };
      persistState(state);
    }

    return { rows: [] };
  }

  if (normalized.includes('insert or ignore into conversations')) {
    const [peerPubkey] = params as [string];
    if (!state.conversations.some((conversation) => conversation.peer_pubkey === peerPubkey)) {
      state.conversations.push({ peer_pubkey: peerPubkey, last_message: null, last_message_at: null, unread_count: 0 });
      persistState(state);
    }
    return { rows: [] };
  }

  if (normalized.includes('update conversations set last_message = ?, last_message_at = ?, unread_count = 0 where peer_pubkey = ?')) {
    return updateConversation(state, params as [string, number, string], true);
  }

  if (normalized.includes('update conversations set last_message = ?, last_message_at = ? where peer_pubkey = ?')) {
    return updateConversation(state, params as [string, number, string], false);
  }

  if (normalized.includes('update conversations set unread_count = unread_count + 1 where peer_pubkey = ?')) {
    const [peerPubkey] = params as [string];
    const existingIndex = state.conversations.findIndex((conversation) => conversation.peer_pubkey === peerPubkey);
    if (existingIndex >= 0) {
      state.conversations[existingIndex] = { ...state.conversations[existingIndex], unread_count: state.conversations[existingIndex].unread_count + 1 };
      persistState(state);
    }
    return { rows: [] };
  }

  if (normalized.includes('update conversations set unread_count = 0 where peer_pubkey = ?')) {
    const [peerPubkey] = params as [string];
    const existingIndex = state.conversations.findIndex((conversation) => conversation.peer_pubkey === peerPubkey);
    if (existingIndex >= 0) {
      state.conversations[existingIndex] = { ...state.conversations[existingIndex], unread_count: 0 };
      persistState(state);
    }
    return { rows: [] };
  }

  if (normalized.includes('delete from conversations where peer_pubkey = ?')) {
    const [peerPubkey] = params as [string];
    state.conversations = state.conversations.filter((conversation) => conversation.peer_pubkey !== peerPubkey);
    persistState(state);
    return { rows: [] };
  }

  if (normalized.includes('select last_message_at from conversations where peer_pubkey = ? and last_message = ? limit 1')) {
    const [peerPubkey, lastMessage] = params as [string, string];
    const conversation = state.conversations.find((entry) => entry.peer_pubkey === peerPubkey && entry.last_message === lastMessage);
    return { rows: conversation ? [{ last_message_at: conversation.last_message_at }] : [] };
  }

  if (
    normalized.includes('select * from conversations')
    && normalized.includes('where last_message is null or last_message != ?')
    && normalized.includes('order by')
  ) {
    const [deletedMarker] = params as [string];
    const rows = state.conversations
      .filter((conversation) => conversation.last_message == null || conversation.last_message !== deletedMarker)
      .sort((left, right) => {
        if (left.last_message_at == null && right.last_message_at != null) return 1;
        if (left.last_message_at != null && right.last_message_at == null) return -1;
        if (left.last_message_at != null && right.last_message_at != null && left.last_message_at !== right.last_message_at) {
          return right.last_message_at - left.last_message_at;
        }
        return left.peer_pubkey.localeCompare(right.peer_pubkey);
      });
    return { rows };
  }

  return null;
}

function updateConversation(
  state: WebDbState,
  [message, timestamp, peerPubkey]: [string, number, string],
  resetUnread: boolean,
): WebQueryResult {
  const existingIndex = state.conversations.findIndex((conversation) => conversation.peer_pubkey === peerPubkey);
  if (existingIndex >= 0) {
    state.conversations[existingIndex] = {
      ...state.conversations[existingIndex],
      last_message: message,
      last_message_at: timestamp,
      unread_count: resetUnread ? 0 : state.conversations[existingIndex].unread_count,
    };
    persistState(state);
  }
  return { rows: [] };
}
