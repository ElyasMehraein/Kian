import { create } from 'zustand';

import { cartService } from '@/services/cart';
import type { CartItem, Product } from '@/types';

interface CartState {
  items: CartItem[];
  hydrate: () => void;
  addItem: (product: Product, quantity: number) => void;
  removeItem: (productId: string) => void;
  setQuantity: (productId: string, quantity: number) => void;
  clear: () => void;
}

function syncItems(): Pick<CartState, 'items'> {
  return { items: cartService.list() };
}

export const useCartStore = create<CartState>((set) => ({
  items: [],
  hydrate: () => {
    set(syncItems());
  },
  addItem: (product, quantity) => {
    cartService.add(product, quantity);
    set(syncItems());
  },
  removeItem: (productId) => {
    cartService.remove(productId);
    set(syncItems());
  },
  setQuantity: (productId, quantity) => {
    cartService.setQuantity(productId, quantity);
    set(syncItems());
  },
  clear: () => {
    cartService.clear();
    set(syncItems());
  },
}));
