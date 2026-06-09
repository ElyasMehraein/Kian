import { productsRepo } from '@/db/repos';
import { productEvents } from '@/services/product-events';
import type { NostrEvent, Product } from '@/types';

type ProductContent = Partial<
  Pick<Product, 'name' | 'description' | 'images' | 'categories' | 'geohash'>
> & {
  title?: string;
  image?: string;
  category?: string;
};

function parseProductContent(content: string): ProductContent {
  return JSON.parse(content) as ProductContent;
}

function getDTag(event: NostrEvent): string {
  const tag = event.tags.find(([name]) => name === 'd');

  if (!tag?.[1]) {
    throw new Error('Missing d tag');
  }

  return tag[1];
}

function toArray(value: string[] | string | undefined): string[] {
  if (Array.isArray(value)) {
    return value;
  }

  if (!value) {
    return [];
  }

  return [value];
}

export async function handleProduct(event: NostrEvent): Promise<void> {
  const content = parseProductContent(event.content);

  await productsRepo.upsert({
    id: getDTag(event),
    pubkey: event.pubkey,
    name: content.name ?? content.title ?? '',
    description: content.description ?? '',
    images: toArray(content.images ?? content.image),
    categories: toArray(content.categories ?? content.category),
    geohash: content.geohash,
    created_at: event.created_at,
    event_id: event.id,
  });
  productEvents.emit();
}
