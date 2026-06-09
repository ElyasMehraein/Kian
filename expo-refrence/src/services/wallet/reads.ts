import { utxoRepo } from '@/db/repos';
import type { TokenUtxo } from '@/types';

import { getPendingConfirmations } from './pending';
import { getOwnPubkey } from './shared';
import type { PendingTokenConfirmation } from './types';

function sumBalances(utxos: TokenUtxo[]): Record<string, number> {
  return utxos.reduce<Record<string, number>>((balances, utxo) => {
    balances[utxo.asset_ref] = (balances[utxo.asset_ref] ?? 0) + utxo.amount;
    return balances;
  }, {});
}

export async function getBalanceByAsset(): Promise<Record<string, number>> {
  const pubkey = await getOwnPubkey();

  if (!pubkey) {
    return {};
  }

  return sumBalances(await utxoRepo.getUnspentByOwner(pubkey));
}

export async function getUTXOs(): Promise<TokenUtxo[]> {
  const pubkey = await getOwnPubkey();

  if (!pubkey) {
    return [];
  }

  return utxoRepo.getUnspentByOwner(pubkey);
}

export async function getPendingConfirmationByAsset(): Promise<Record<string, number>> {
  const pubkey = await getOwnPubkey();

  if (!pubkey) {
    return {};
  }

  const pending = await getPendingConfirmations(pubkey);

  return pending.reduce<Record<string, number>>((balances, item) => {
    balances[item.asset_ref] = (balances[item.asset_ref] ?? 0) + item.amount;
    return balances;
  }, {});
}

export async function listPendingConfirmations(): Promise<PendingTokenConfirmation[]> {
  const pubkey = await getOwnPubkey();

  if (!pubkey) {
    return [];
  }

  return getPendingConfirmations(pubkey);
}
