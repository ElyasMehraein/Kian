import { productsRepo } from '@/db/repos';
import { productEvents } from '@/services/product-events';
import type { NostrEvent } from '@/types';

function getDeletedEventIds(event: NostrEvent): string[] {
  return event.tags
    .filter(([name, value]) => name === 'e' && Boolean(value))
    .map(([, value]) => value as string);
}

export async function handleDelete(event: NostrEvent): Promise<void> {
  const eventIds = getDeletedEventIds(event);

  if (eventIds.length === 0) {
    return;
  }

  await productsRepo.deleteByEventIds(event.pubkey, eventIds);
  productEvents.emit();
}
