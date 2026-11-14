'use client';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api } from '@/lib/api';

// ---------------------------------------------------------------- toasts
type Toast = { id: number; tone: 'success' | 'error' | 'info'; message: string; action?: { label: string; href: string } };
const ToastCtx = createContext<(t: Omit<Toast, 'id'>) => void>(() => {});
export const useToast = () => useContext(ToastCtx);

// ---------------------------------------------------------------- cart summary (badge)
type CartSummary = { regularUnits: number; bulkUnits: number };
const CartCtx = createContext<{ summary: CartSummary; refresh: () => void }>({ summary: { regularUnits: 0, bulkUnits: 0 }, refresh: () => {} });
export const useCartSummary = () => useContext(CartCtx);

// ---------------------------------------------------------------- wishlist (device-local convenience only)
const WISHLIST_KEY = 'aurelia.wishlist.v1';
const WishCtx = createContext<{ slugs: string[]; toggle: (slug: string) => void; has: (slug: string) => boolean }>({
  slugs: [], toggle: () => {}, has: () => false,
});
export const useWishlist = () => useContext(WishCtx);

function readWishlist(): string[] {
  try {
    const raw = window.localStorage.getItem(WISHLIST_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === 'string' && /^[a-z0-9-]{1,120}$/.test(s)).slice(0, 50) : [];
  } catch {
    return [];
  }
}

export function Providers({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const seq = useRef(0);
  const push = useCallback((t: Omit<Toast, 'id'>) => {
    const id = ++seq.current;
    setToasts((all) => [...all.slice(-2), { ...t, id }]);
    window.setTimeout(() => setToasts((all) => all.filter((x) => x.id !== id)), 5000);
  }, []);

  const [summary, setSummary] = useState<CartSummary>({ regularUnits: 0, bulkUnits: 0 });
  const refresh = useCallback(() => {
    api<CartSummary>('/api/v1/cart/summary').then(setSummary).catch(() => {});
  }, []);
  useEffect(() => { refresh(); }, [refresh]);

  const [slugs, setSlugs] = useState<string[]>([]);
  useEffect(() => { setSlugs(readWishlist()); }, []);
  const toggle = useCallback((slug: string) => {
    setSlugs((prev) => {
      const next = prev.includes(slug) ? prev.filter((s) => s !== slug) : [slug, ...prev].slice(0, 50);
      try { window.localStorage.setItem(WISHLIST_KEY, JSON.stringify(next)); } catch { /* storage unavailable */ }
      return next;
    });
  }, []);
  const wish = useMemo(() => ({ slugs, toggle, has: (s: string) => slugs.includes(s) }), [slugs, toggle]);
  const cart = useMemo(() => ({ summary, refresh }), [summary, refresh]);

  return (
    <ToastCtx.Provider value={push}>
      <CartCtx.Provider value={cart}>
        <WishCtx.Provider value={wish}>
          {children}
          <div aria-live="polite" aria-atomic="false" className="pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 sm:bottom-6 sm:items-end sm:pr-6">
            {toasts.map((t) => (
              <div key={t.id} role={t.tone === 'error' ? 'alert' : 'status'}
                className={`pointer-events-auto flex max-w-sm items-center gap-4 rounded-xl px-5 py-3.5 text-sm shadow-xl ring-1 ${
                  t.tone === 'error' ? 'bg-white text-danger ring-danger/30' : 'bg-plum text-ivory ring-black/10'}`}>
                <span>{t.message}</span>
                {t.action ? <a href={t.action.href} className="shrink-0 font-semibold underline underline-offset-4">{t.action.label}</a> : null}
              </div>
            ))}
          </div>
        </WishCtx.Provider>
      </CartCtx.Provider>
    </ToastCtx.Provider>
  );
}
