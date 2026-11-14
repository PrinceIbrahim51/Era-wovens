import 'server-only';
import type { Collection, Listing, Product } from './types';

// Server-side reads go straight to the gateway (never exposed to the browser bundle).
const GATEWAY = process.env.GATEWAY_URL ?? 'http://localhost:8080';

export class NotFoundError extends Error {}

async function get<T>(path: string, revalidate = 30): Promise<T> {
  const res = await fetch(`${GATEWAY}${path}`, {
    next: { revalidate },
    headers: { Accept: 'application/json', 'X-Forwarded-For': '127.0.0.1' },
  });
  if (res.status === 404) throw new NotFoundError(path);
  if (!res.ok) throw new Error(`Gateway ${res.status} for ${path}`);
  return res.json() as Promise<T>;
}

export const getCollections = () => get<Collection[]>('/api/v1/catalog/collections', 300);

export function getListing(params: Record<string, string | undefined>) {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) qs.set(k, v);
  return get<Listing>(`/api/v1/catalog/products?${qs.toString()}`);
}

export const getProduct = (slug: string) =>
  get<{ product: Product; related: Product[]; availabilityKnown: boolean }>(`/api/v1/catalog/products/${encodeURIComponent(slug)}`);
