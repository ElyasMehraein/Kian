import { EVENT_KIND } from '@/types';
import type { NostrEvent } from '@/types';
import * as handlers from '@/handlers';

type EventHandler = (event: NostrEvent) => Promise<void>;
const handlerRegistry = handlers as Record<string, unknown>;

function hasValidEventShape(event: NostrEvent): boolean {
  return (
    typeof event.id === 'string' &&
    typeof event.pubkey === 'string' &&
    typeof event.created_at === 'number' &&
    typeof event.kind === 'number' &&
    Array.isArray(event.tags) &&
    typeof event.content === 'string' &&
    typeof event.sig === 'string'
  );
}

function getHandler(kind: number): EventHandler | null {
  switch (kind) {
    case EVENT_KIND.DELETE:
      return (handlerRegistry.handleDelete as EventHandler | undefined) ?? null;
    case EVENT_KIND.FOLLOW_LIST:
      return (
        handlerRegistry.handleFollowList as EventHandler | undefined
      ) ?? null;
    case EVENT_KIND.METADATA:
      return (handlerRegistry.handleMetadata as EventHandler | undefined) ?? null;
    case EVENT_KIND.DM_RELAY_LIST:
      return (handlerRegistry.handleDmRelayList as EventHandler | undefined) ?? null;
    case EVENT_KIND.GIFT_WRAP:
      return (handlerRegistry.handleChatMessage as EventHandler | undefined) ?? null;
    case EVENT_KIND.RECEIPT_DELIVERED:
      return (handlerRegistry.handleDelivered as EventHandler | undefined) ?? null;
    case EVENT_KIND.RECEIPT_READ:
      return (handlerRegistry.handleRead as EventHandler | undefined) ?? null;
    case EVENT_KIND.PRODUCT:
      return (handlerRegistry.handleProduct as EventHandler | undefined) ?? null;
    case EVENT_KIND.REVIEW:
      return (handlerRegistry.handleReview as EventHandler | undefined) ?? null;
    case EVENT_KIND.TOKEN_MINT:
      return (
        handlerRegistry.handleTokenDefinition as EventHandler | undefined
      ) ?? null;
    case EVENT_KIND.TOKEN_UTXO:
      return (handlerRegistry.handleTokenUTXO as EventHandler | undefined) ?? null;
    case EVENT_KIND.TRANSFER_REQUEST:
      return (
        handlerRegistry.handleTransferRequest as EventHandler | undefined
      ) ?? null;
    default:
      return null;
  }
}

export async function dispatchEvent(event: NostrEvent): Promise<void> {
  if (!hasValidEventShape(event)) {
    return;
  }

  const handler = getHandler(event.kind);

  if (!handler) {
    return;
  }

  try {
    await handler(event);
  } catch {
    return;
  }
}
