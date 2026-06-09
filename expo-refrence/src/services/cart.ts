import type { CartItem, Product } from '@/types';

let cartItems: CartItem[] = [];

function findItemIndex(productId: string): number {
  return cartItems.findIndex((item) => item.product.id === productId);
}

export const cartService = {
  add(product: Product, qty: number): void {
    const index = findItemIndex(product.id);

    if (index >= 0) {
      const current = cartItems[index];

      cartItems[index] = {
        product: current.product,
        quantity: current.quantity + qty,
      };
      return;
    }

    cartItems = [...cartItems, { product, quantity: qty }];
  },

  remove(productId: string): void {
    cartItems = cartItems.filter((item) => item.product.id !== productId);
  },

  setQuantity(productId: string, quantity: number): void {
    if (quantity <= 0) {
      this.remove(productId);
      return;
    }

    cartItems = cartItems.map((item) =>
      item.product.id === productId ? { ...item, quantity } : item,
    );
  },

  clear(): void {
    cartItems = [];
  },

  list(): CartItem[] {
    return [...cartItems];
  },
};
