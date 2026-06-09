import {
  buildTokenDefinition,
  buildTokenUTXO,
  buildTransferRequest,
} from '@/builders';
import { signEvent } from '@/crypto';
import {
  keysRepo,
  productCategoriesRepo,
  productsRepo,
  tokenDefinitionsRepo,
} from '@/db/repos';
import { publishOrQueue as publishRelayEventOrQueue } from '@/services/chat-transport';
import { TOKEN_UTXO_TAG_PREFIX } from '@/types';
import type { ProductRequest, TokenDefinition, TokenUtxo } from '@/types';

export async function getOwnPubkey(): Promise<string | null> {
  return keysRepo.getPublicKey();
}

export async function getWalletKeys(): Promise<{ pubkey: string; privkey: string }> {
  const [pubkey, privkey] = await Promise.all([
    keysRepo.getPublicKey(),
    keysRepo.getPrivateKey(),
  ]);

  if (!pubkey || !privkey) {
    throw new Error('Wallet keys are unavailable');
  }

  return { pubkey, privkey };
}

export function parseAssetRef(assetRef: string): { producer: string; assetId: string } | null {
  const [kind, producer, assetId] = assetRef.split(':', 3);

  return kind === TOKEN_UTXO_TAG_PREFIX && producer && assetId
    ? { producer, assetId }
    : null;
}

export async function publishWalletEvent(event: ReturnType<typeof signEvent>): Promise<boolean> {
  return publishRelayEventOrQueue(event);
}

export async function ensureTokenDefinition(request: ProductRequest, pubkey: string, privkey: string): Promise<TokenDefinition> {
  const existing = await tokenDefinitionsRepo.get(request.product_id, pubkey);

  if (existing) {
    return existing;
  }

  const product = await productsRepo.get(request.product_id, pubkey);

  if (!product) {
    throw new Error('Product definition unavailable for minting');
  }

  const categoryNames = product.categories.length > 0
    ? (await productCategoriesRepo.listByPubkey(pubkey))
      .filter((category) => product.categories.includes(category.id))
      .map((category) => category.name)
    : [];

  const definition: TokenDefinition = {
    asset_id: request.product_id,
    pubkey,
    product_id: request.product_id,
    name: product.name,
    description: product.description,
    images: product.images,
    categories: categoryNames,
    unit: 'unit',
    created_at: Math.floor(Date.now() / 1000),
  };

  const signedDefinition = signEvent(buildTokenDefinition(definition), privkey);
  await tokenDefinitionsRepo.upsert(definition);
  await publishWalletEvent(signedDefinition);

  return definition;
}

export function findMatchingUtxo(utxos: TokenUtxo[], request: ProductRequest): TokenUtxo | null {
  return (
    utxos.find((utxo) => {
      const parsed = parseAssetRef(utxo.asset_ref);

      return (
        parsed?.producer === request.producer_pubkey
        && parsed.assetId === request.product_id
        && utxo.amount >= request.quantity
      );
    }) ?? null
  );
}

export { buildTokenUTXO, buildTransferRequest, signEvent, TOKEN_UTXO_TAG_PREFIX };
