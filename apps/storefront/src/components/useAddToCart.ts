'use client';
import { useState } from 'react';
import { api, ApiError } from '@/lib/api';
import type { Cart, CartType } from '@/lib/types';
import { useCartSummary, useOrderingOpen, useToast } from './Providers';

export function useAddToCart() {
  const toast = useToast();
  const { refresh } = useCartSummary();
  const [pending, setPending] = useState(false);
  const open = useOrderingOpen();

  async function add(sku: string, quantity: number, cartType: CartType = 'REGULAR'): Promise<Cart | null> {
    setPending(true);
    try {
      const cart = await api<Cart>('/api/v1/cart/items', { method: 'POST', json: { sku, quantity, cartType } });
      refresh();
      toast({
        tone: 'success',
        message: cartType === 'BULK' ? `Added ${quantity} to your bulk order` : 'Added to your bag',
        action: { label: 'View', href: cartType === 'BULK' ? '/cart?type=bulk' : '/cart' },
      });
      return cart;
    } catch (e) {
      toast({ tone: 'error', message: e instanceof ApiError ? e.message : 'Could not add to cart.' });
      return null;
    } finally {
      setPending(false);
    }
  }
  return { add, pending, open };
}
