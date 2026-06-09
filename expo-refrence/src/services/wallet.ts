import { fulfillProductRequest, mintProductForChat, sendTokenTransfer } from './wallet/actions';
import {
  getBalanceByAsset,
  getPendingConfirmationByAsset,
  getUTXOs,
  listPendingConfirmations,
} from './wallet/reads';

export const walletService = {
  getBalanceByAsset,
  getUTXOs,
  getPendingConfirmationByAsset,
  getPendingConfirmations: listPendingConfirmations,
  mintProductForChat,
  fulfillProductRequest,
  sendTokenTransfer,
};
