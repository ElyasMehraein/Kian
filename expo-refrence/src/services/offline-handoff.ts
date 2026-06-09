const SMS_PREFIX = 'KIAN-OFFLINE';
const MAX_SMS_CHUNK_SIZE = 120;

function stripSmsHeaders(payload: string): string {
  const parts = payload
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);

  const smsParts = parts.filter((line) => line.startsWith(`${SMS_PREFIX}:`));

  if (smsParts.length === 0) {
    return payload.trim();
  }

  return smsParts
    .map((line) => {
      const match = line.match(/^KIAN-OFFLINE:(\d+)\/(\d+):(.*)$/);
      return match ? match[3] : '';
    })
    .join('');
}

export function createOfflineSmsMessage(payload: string): string {
  const normalized = payload.trim();

  if (!normalized) {
    return '';
  }

  const chunks =
    normalized.length <= MAX_SMS_CHUNK_SIZE
      ? [normalized]
      : normalized.match(new RegExp(`.{1,${MAX_SMS_CHUNK_SIZE}}`, 'g')) ?? [normalized];

  return chunks
    .map((chunk, index) => `${SMS_PREFIX}:${index + 1}/${chunks.length}:${chunk}`)
    .join('\n');
}

export function extractOfflineSmsPayload(payload: string): string {
  return stripSmsHeaders(payload);
}
