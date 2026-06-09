import { EVENT_KIND } from '@/types';
import type { Product, UnsignedEvent } from '@/types';

type ProductEventInput = Product & {
  tags?: string[][];
  d_tag?: string;
};

function serializeProduct(product: Product): string {
  return JSON.stringify({
    name: product.name,
    description: product.description,
    images: product.images,
    categories: product.categories,
    geohash: product.geohash,
  });
}

export function getProductDTagValue(product: Product): string {
  const input = product as ProductEventInput;

  return input.d_tag ?? product.id;
}

function buildProductTags(product: Product): string[][] {
  const input = product as ProductEventInput;
  const tags = (input.tags ?? []).filter(([name]) => name !== 'd');

  return [['d', getProductDTagValue(product)], ...tags];
}

export function buildProduct(product: Product): UnsignedEvent {
  return {
    pubkey: product.pubkey,
    created_at: product.created_at,
    kind: EVENT_KIND.PRODUCT,
    tags: buildProductTags(product),
    content: serializeProduct(product),
  };
}
