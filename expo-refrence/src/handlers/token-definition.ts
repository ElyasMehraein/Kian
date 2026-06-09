import { tokenDefinitionsRepo } from '@/db/repos';
import { messageEvents } from '@/services/message-events';
import type { NostrEvent, TokenDefinition } from '@/types';

type TokenDefinitionContent = Partial<
  Pick<TokenDefinition, 'product_id' | 'name' | 'description' | 'images' | 'categories' | 'unit'>
> & {
  title?: string;
};

function parseTokenDefinitionContent(content: string): TokenDefinitionContent {
  return JSON.parse(content) as TokenDefinitionContent;
}

function getTagValue(event: NostrEvent, tagName: string): string | undefined {
  return event.tags.find(([name]) => name === tagName)?.[1];
}

function getAssetId(event: NostrEvent): string {
  const assetId = getTagValue(event, 'd');

  if (!assetId) {
    throw new Error('Missing d tag');
  }

  return assetId;
}

export async function handleTokenDefinition(event: NostrEvent): Promise<void> {
  const content = parseTokenDefinitionContent(event.content);

  await tokenDefinitionsRepo.upsert({
    asset_id: getAssetId(event),
    pubkey: event.pubkey,
    product_id: content.product_id ?? '',
    name: content.name ?? content.title ?? '',
    description: content.description ?? '',
    images: Array.isArray(content.images) ? content.images : [],
    categories: Array.isArray(content.categories) ? content.categories : [],
    unit: content.unit ?? 'unit',
    event_id: event.id,
    created_at: event.created_at,
  } as TokenDefinition & { event_id: string });
  messageEvents.emit();
}
