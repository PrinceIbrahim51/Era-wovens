import 'server-only';
import seed from '@/data/catalog.json';
import type { Collection, Listing, Motif, Product } from './types';

type SeedVariant = { color: string; family: string; hex: string; accent: string; stock: number };
type SeedProduct = {
  slug: string; name: string; collection: string; fabric: string; craft: string; occasion: string; price: number;
  bulkPrice?: number; motif: string; isNew?: boolean; description: string; variants: SeedVariant[];
};

const collections: Collection[] = seed.collections.map((c) => ({ slug: c.slug, name: c.name, description: c.description, bulk: c.bulk }));
const seedProducts = seed.products as SeedProduct[];
const sku = (p: SeedProduct, i: number) => `${p.slug.split('-')[0]!.toUpperCase().slice(0, 8)}-${String(i + 1).padStart(2, '0')}`;

const products: (Product & { order: number; featured: number | null })[] = seedProducts.map((p, idx) => {
  const col = collections.find((c) => c.slug === p.collection)!;
  const variants = p.variants.map((v, i) => ({ sku: sku(p, i), colorName: v.color, colorFamily: v.family, colorHex: v.hex, accentHex: v.accent, inStock: v.stock > 0 }));
  return {
    slug: p.slug, name: p.name, collection: { slug: col.slug, name: col.name }, fabric: p.fabric, craft: p.craft,
    occasion: p.occasion, pricePaise: p.price * 100, bulkPricePaise: p.bulkPrice ? p.bulkPrice * 100 : undefined,
    bulkEligible: !!p.bulkPrice, displayPricePaise: p.price * 100, motif: p.motif as Motif, isNew: !!p.isNew,
    inStock: variants.some((v) => v.inStock), variants, description: p.description,
    maxPerOrder: p.bulkPrice ? 10 : 5, order: idx, featured: idx < 8 ? idx + 1 : null,
  };
});

const strip = ({ order: _o, featured: _f, ...p }: (typeof products)[number], bulk = false, detail = false): Product => ({
  ...p,
  displayPricePaise: bulk && p.bulkPricePaise ? p.bulkPricePaise : p.pricePaise,
  description: detail ? p.description : undefined,
  maxPerOrder: detail ? p.maxPerOrder : undefined,
});

export class StaticNotFound extends Error {}

export const staticCollections = () => collections;

export function staticListing(params: Record<string, string | undefined>): Listing {
  const collection = params.collection && params.collection !== 'all' ? collections.find((c) => c.slug === params.collection) : undefined;
  if (params.collection && params.collection !== 'all' && !collection) throw new StaticNotFound(params.collection);
  const bulk = collection?.bulk ?? false;
  const words = (params.q ?? '').toLowerCase().split(/\s+/).filter(Boolean);
  let list = products.filter((p) => (!collection ? true : bulk ? p.bulkEligible : p.collection.slug === collection.slug));
  if (words.length) {
    list = list.filter((p) => {
      const hay = [p.name, p.fabric, p.craft, p.occasion, p.motif, p.description, ...p.variants.flatMap((v) => [v.colorName, v.colorFamily])].join(' ').toLowerCase();
      return words.every((w) => hay.includes(w));
    });
  }
  const price = (p: Product) => (bulk && p.bulkPricePaise ? p.bulkPricePaise : p.pricePaise);
  const colorCount = new Map<string, { hex: string; count: number }>();
  const fabricCount = new Map<string, number>();
  for (const p of list) {
    for (const f of new Set(p.variants.map((v) => v.colorFamily))) {
      const hex = p.variants.find((v) => v.colorFamily === f)!.colorHex;
      colorCount.set(f, { hex: colorCount.get(f)?.hex ?? hex, count: (colorCount.get(f)?.count ?? 0) + 1 });
    }
    fabricCount.set(p.fabric, (fabricCount.get(p.fabric) ?? 0) + 1);
  }
  const facets: Listing['facets'] = {
    colors: [...colorCount].sort(([a], [b]) => a.localeCompare(b)).map(([family, v]) => ({ family, ...v })),
    fabrics: [...fabricCount].sort(([a], [b]) => a.localeCompare(b)).map(([value, count]) => ({ value, count })),
    minPricePaise: list.length ? Math.min(...list.map(price)) : 0,
    maxPricePaise: list.length ? Math.max(...list.map(price)) : 0,
  };
  const colors = (params.color ?? '').split(',').filter(Boolean);
  const fabrics = (params.fabric ?? '').split(',').filter(Boolean);
  if (colors.length) list = list.filter((p) => p.variants.some((v) => colors.includes(v.colorFamily)));
  if (fabrics.length) list = list.filter((p) => fabrics.includes(p.fabric));
  if (params.minPrice) list = list.filter((p) => price(p) >= Number(params.minPrice) * 100);
  if (params.maxPrice) list = list.filter((p) => price(p) <= Number(params.maxPrice) * 100);
  if (params.inStock === 'true') list = list.filter((p) => p.inStock);
  const newest = (a: (typeof list)[number], b: (typeof list)[number]) => b.order - a.order;
  const sorters: Record<string, (a: (typeof list)[number], b: (typeof list)[number]) => number> = {
    newest,
    price_asc: (a, b) => price(a) - price(b) || newest(a, b),
    price_desc: (a, b) => price(b) - price(a) || newest(a, b),
    name_asc: (a, b) => a.name.localeCompare(b.name),
    featured: (a, b) => (a.featured ?? 1e9) - (b.featured ?? 1e9) || newest(a, b),
  };
  list = [...list].sort(sorters[params.sort ?? 'featured'] ?? sorters.featured);
  const size = Math.min(48, Math.max(1, Number(params.size ?? 12)));
  const total = list.length;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const page = Math.min(100, Math.max(1, Number(params.page ?? 1)));
  return {
    collection, items: list.slice((page - 1) * size, page * size).map((p) => strip(p, bulk)), page, size, total, totalPages, facets,
    availabilityKnown: true,
  };
}

export function staticProduct(slug: string) {
  const p = products.find((x) => x.slug === slug);
  if (!p) throw new StaticNotFound(slug);
  const related = products.filter((r) => r.collection.slug === p.collection.slug && r.slug !== slug).sort((a, b) => b.order - a.order).slice(0, 4);
  return { product: strip(p, false, true), related: related.map((r) => strip(r)), availabilityKnown: true };
}

export const staticBySlugs = (slugs: string[]) =>
  slugs.map((s) => products.find((p) => p.slug === s)).filter((p): p is (typeof products)[number] => !!p).map((p) => strip(p));
