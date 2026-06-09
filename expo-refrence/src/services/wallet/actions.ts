import { utxoRepo } from '@/db/repos';
import { chatService } from '@/services/chat';
import type { Product, ProductRequest, TokenTransfer } from '@/types';

import {
  buildTokenUTXO,
  buildTransferRequest,
  ensureTokenDefinition,
  findMatchingUtxo,
  getWalletKeys,
  publishWalletEvent,
  signEvent,
  TOKEN_UTXO_TAG_PREFIX,
} from './shared';
import type { FulfillProductRequestResult, MintProductForChatResult } from './types';

export async function sendTokenTransfer(
  utxoId: string,
  amount: number,
  recipientPubkey: string,
): Promise<void> {
  const { pubkey, privkey } = await getWalletKeys();
  const utxo = await utxoRepo.get(utxoId);

  if (!utxo) {
    throw new Error('Selected token entry is unavailable');
  }

  if (utxo.owner !== pubkey) {
    throw new Error('You can only send token entries you own');
  }

  if (!recipientPubkey) {
    throw new Error('Recipient is required');
  }

  if (!Number.isInteger(amount) || amount <= 0 || amount > utxo.amount) {
    throw new Error('Enter a valid token amount');
  }

  const transfer: TokenTransfer = {
    utxo_id: utxo.utxo_id,
    asset_ref: utxo.asset_ref,
    amount,
    sender: pubkey,
    recipient: recipientPubkey,
  };
  const messageId = await chatService.sendTokenTransfer(recipientPubkey, transfer);
  const transferEvent = signEvent(
    buildTransferRequest(pubkey, utxo.utxo_id, amount, recipientPubkey, utxo.producer),
    privkey,
  );

  await utxoRepo.markSpent(utxo.utxo_id);
  await publishWalletEvent(transferEvent);
  await chatService.markTokenTransferWaitingForIssuer(recipientPubkey, messageId, utxo.utxo_id);
}

export async function mintProductForChat(
  recipientPubkey: string,
  product: Product,
  quantity: number,
): Promise<MintProductForChatResult> {
  const { pubkey, privkey } = await getWalletKeys();

  if (!recipientPubkey) {
    throw new Error('Recipient is required');
  }

  if (product.pubkey !== pubkey) {
    throw new Error('You can only mint tokens for your own products');
  }

  if (!Number.isInteger(quantity) || quantity <= 0) {
    throw new Error('Enter a valid integer quantity');
  }

  const request: ProductRequest = {
    product_id: product.id,
    product_name: product.name,
    quantity,
    producer_pubkey: pubkey,
  };
  const definition = await ensureTokenDefinition(request, pubkey, privkey);
  const assetRef = `${TOKEN_UTXO_TAG_PREFIX}:${pubkey}:${definition.asset_id}`;
  const signedUtxo = signEvent(
    buildTokenUTXO(recipientPubkey, quantity, assetRef),
    privkey,
  );

  await utxoRepo.insert({
    utxo_id: signedUtxo.id,
    asset_ref: assetRef,
    producer: pubkey,
    owner: recipientPubkey,
    amount: quantity,
    created_at: signedUtxo.created_at,
    spent: false,
  });
  await publishWalletEvent(signedUtxo);

  return {
    minted: {
      product_id: product.id,
      product_name: product.name,
      product_description: definition.description,
      product_images: definition.images,
      product_categories: definition.categories,
      quantity,
      utxo_ids: [signedUtxo.id],
      asset_ref: assetRef,
    },
  };
}

export async function fulfillProductRequest(
  requesterPubkey: string,
  request: ProductRequest,
): Promise<FulfillProductRequestResult> {
  const { pubkey, privkey } = await getWalletKeys();

  if (!Number.isInteger(request.quantity) || request.quantity <= 0) {
    throw new Error('Invalid product request quantity');
  }

  if (pubkey === request.producer_pubkey) {
    const definition = await ensureTokenDefinition(request, pubkey, privkey);
    const assetRef = `${TOKEN_UTXO_TAG_PREFIX}:${pubkey}:${definition.asset_id}`;
    const signedUtxo = signEvent(
      buildTokenUTXO(requesterPubkey, request.quantity, assetRef),
      privkey,
    );

    await utxoRepo.insert({
      utxo_id: signedUtxo.id,
      asset_ref: assetRef,
      producer: pubkey,
      owner: requesterPubkey,
      amount: request.quantity,
      created_at: signedUtxo.created_at,
      spent: false,
    });
    await publishWalletEvent(signedUtxo);

    return { status: 'fulfilled' };
  }

  const ownedUtxos = await utxoRepo.getUnspentByOwner(pubkey);
  const matchingUtxo = findMatchingUtxo(ownedUtxos, request);

  if (!matchingUtxo) {
    throw new Error('No matching token available to fulfill this request');
  }

  const transferEvent = signEvent(
    buildTransferRequest(
      pubkey,
      matchingUtxo.utxo_id,
      request.quantity,
      requesterPubkey,
      matchingUtxo.producer,
    ),
    privkey,
  );

  await publishWalletEvent(transferEvent);

  return {
    status: 'waiting_mint',
    transferUtxoId: matchingUtxo.utxo_id,
  };
}
