import { profilesRepo } from '@/db/repos';
import { useUIStore } from '@/stores/ui';
import type { NostrEvent, Profile } from '@/types';

type MetadataContent = Partial<
  Pick<
    Profile,
    'display_name' | 'about' | 'picture' | 'nip05' | 'lud16' | 'geohash'
  >
>;

type StoredMetadata = Profile & {
  is_trader?: boolean;
  tags?: string[][];
};

function parseMetadataContent(content: string): MetadataContent {
  return JSON.parse(content) as MetadataContent;
}

function hasMerchantTag(tags: string[][]): boolean {
  return tags.some(([name, value]) => name === 't' && value === 'trader');
}

export async function handleMetadata(event: NostrEvent): Promise<void> {
  const metadata = parseMetadataContent(event.content);

  await profilesRepo.upsert({
    pubkey: event.pubkey,
    created_at: event.created_at,
    display_name: metadata.display_name,
    about: metadata.about,
    picture: metadata.picture,
    nip05: metadata.nip05,
    lud16: metadata.lud16,
    geohash: metadata.geohash,
    is_trader: hasMerchantTag(event.tags),
    tags: event.tags,
  } as StoredMetadata);
  useUIStore.getState().bumpProfileVersion();
}
