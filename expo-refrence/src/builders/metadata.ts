import { EVENT_KIND } from '@/types';
import type { Profile, UnsignedEvent } from '@/types';

type MetadataProfile = Profile & {
  is_trader?: boolean;
  tags?: string[][];
};

function serializeProfile(profile: MetadataProfile): string {
  const content = {
    display_name: profile.display_name,
    about: profile.about,
    picture: profile.picture,
    nip05: profile.nip05,
    lud16: profile.lud16,
    geohash: profile.geohash,
  };

  return JSON.stringify(content);
}

function buildTags(profile: MetadataProfile): string[][] {
  const tags = (profile.tags ?? []).filter(
    ([name, value]) => name !== 't' || value !== 'trader',
  );

  if (profile.is_trader) {
    tags.push(['t', 'trader']);
  }

  return tags;
}

export function buildMetadata(profile: Profile): UnsignedEvent {
  const metadataProfile = profile as MetadataProfile;

  return {
    pubkey: profile.pubkey,
    created_at: profile.created_at,
    kind: EVENT_KIND.METADATA,
    tags: buildTags(metadataProfile),
    content: serializeProfile(metadataProfile),
  };
}
