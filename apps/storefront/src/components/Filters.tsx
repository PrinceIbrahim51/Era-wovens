'use client';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useRef, useTransition } from 'react';
import { colorLabel, formatPaise } from '@/lib/format';
import type { Listing } from '@/lib/types';
import { CloseIcon, FilterIcon } from './Icons';

const SORTS: [string, string][] = [['featured', 'Featured'], ['newest', 'Newest'], ['price_asc', 'Price: low to high'], ['price_desc', 'Price: high to low'], ['name_asc', 'Name']];

function useFilterNav() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const [pending, start] = useTransition();
  const set = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params.toString());
    mutate(next);
    next.delete('page');
    start(() => router.push(`${pathname}?${next.toString()}`, { scroll: false }));
  };
  const list = (key: string) => (params.get(key) ?? '').split(',').filter(Boolean);
  const toggleIn = (key: string, value: string) => set((p) => {
    const cur = new Set(list(key));
    if (cur.has(value)) cur.delete(value); else cur.add(value);
    if (cur.size) p.set(key, [...cur].join(',')); else p.delete(key);
  });
  return { params, set, list, toggleIn, pending };
}

export function SortSelect() {
  const { params, set } = useFilterNav();
  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="text-muted">Sort</span>
      <select value={params.get('sort') ?? 'featured'} onChange={(e) => set((p) => p.set('sort', e.target.value))}
        className="rounded-full border border-line bg-white px-4 py-2 text-sm focus:border-plum focus:outline-none">
        {SORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </label>
  );
}

function FilterForm({ facets, idPrefix }: { facets: Listing['facets']; idPrefix: string }) {
  const { params, set, list, toggleIn, pending } = useFilterNav();
  const colors = list('color');
  const fabrics = list('fabric');
  const minRupees = Math.floor(facets.minPricePaise / 100);
  const maxRupees = Math.ceil(facets.maxPricePaise / 100);
  const active = colors.length + fabrics.length + (params.get('minPrice') ? 1 : 0) + (params.get('maxPrice') ? 1 : 0) + (params.get('inStock') ? 1 : 0);

  return (
    <div className={`space-y-8 transition-opacity ${pending ? 'opacity-60' : ''}`} aria-busy={pending}>
      <fieldset>
        <legend className="eyebrow mb-3">Colour</legend>
        <div className="flex flex-wrap gap-2">
          {facets.colors.map((c) => {
            const on = colors.includes(c.family);
            return (
              <label key={c.family} className={`flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition ${on ? 'border-plum bg-plum text-ivory' : 'border-line bg-white hover:border-plum/50'}`}>
                <input type="checkbox" className="sr-only" checked={on} onChange={() => toggleIn('color', c.family)} />
                <span aria-hidden className="h-3.5 w-3.5 rounded-full ring-1 ring-black/10" style={{ background: c.hex }} />
                {colorLabel(c.family)} <span className={on ? 'text-ivory/70' : 'text-muted'}>({c.count})</span>
              </label>
            );
          })}
        </div>
      </fieldset>
      <fieldset>
        <legend className="eyebrow mb-3">Fabric</legend>
        <ul className="space-y-2">
          {facets.fabrics.map((f) => (
            <li key={f.value}>
              <label className="flex cursor-pointer items-center gap-3 text-sm">
                <input type="checkbox" className="h-4 w-4 accent-[var(--color-plum)]" checked={fabrics.includes(f.value)} onChange={() => toggleIn('fabric', f.value)} />
                {f.value} <span className="text-muted">({f.count})</span>
              </label>
            </li>
          ))}
        </ul>
      </fieldset>
      <fieldset>
        <legend className="eyebrow mb-3">Price (₹)</legend>
        <form className="flex items-end gap-2" onSubmit={(e) => {
          e.preventDefault();
          const fd = new FormData(e.currentTarget);
          set((p) => {
            for (const k of ['minPrice', 'maxPrice']) {
              const v = String(fd.get(k) ?? '').replace(/\D/g, '');
              if (v) p.set(k, v); else p.delete(k);
            }
          });
        }}>
          <label className="flex-1 text-xs text-muted">Min
            <input name="minPrice" inputMode="numeric" defaultValue={params.get('minPrice') ?? ''} placeholder={String(minRupees)} className="field mt-1 py-2" id={`${idPrefix}-min`} />
          </label>
          <label className="flex-1 text-xs text-muted">Max
            <input name="maxPrice" inputMode="numeric" defaultValue={params.get('maxPrice') ?? ''} placeholder={String(maxRupees)} className="field mt-1 py-2" id={`${idPrefix}-max`} />
          </label>
          <button className="btn-outline px-4 py-2.5">Go</button>
        </form>
        <p className="mt-2 text-xs text-muted">Range in this view: {formatPaise(facets.minPricePaise)} – {formatPaise(facets.maxPricePaise)}</p>
      </fieldset>
      <label className="flex cursor-pointer items-center justify-between gap-3 text-sm font-medium">
        In stock only
        <input type="checkbox" role="switch" className="h-5 w-9 cursor-pointer appearance-none rounded-full bg-line transition before:block before:h-5 before:w-5 before:rounded-full before:bg-white before:shadow before:transition checked:bg-plum checked:before:translate-x-4"
          checked={params.get('inStock') === 'true'} onChange={(e) => set((p) => { if (e.target.checked) p.set('inStock', 'true'); else p.delete('inStock'); })} />
      </label>
      {active > 0 && (
        <button type="button" className="text-sm font-semibold text-gold-deep underline underline-offset-4"
          onClick={() => set((p) => { ['color', 'fabric', 'minPrice', 'maxPrice', 'inStock'].forEach((k) => p.delete(k)); })}>
          Clear all filters ({active})
        </button>
      )}
    </div>
  );
}

export function Filters({ facets, total }: { facets: Listing['facets']; total: number }) {
  const drawer = useRef<HTMLDialogElement>(null);
  return (
    <>
      <aside className="hidden lg:block" aria-label="Filters">
        <div className="sticky top-32"><FilterForm facets={facets} idPrefix="d" /></div>
      </aside>
      <button type="button" className="btn-outline px-4 py-2 lg:hidden" onClick={() => drawer.current?.showModal()}>
        <FilterIcon width={18} height={18} /> Filters
      </button>
      <dialog ref={drawer} aria-label="Filters" className="m-0 mt-auto max-h-[85dvh] w-full max-w-none rounded-t-[24px] bg-ivory p-0 text-charcoal shadow-2xl">
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-ivory px-5 py-4">
          <span className="font-display text-2xl text-plum">Filters</span>
          <button type="button" aria-label="Close filters" className="rounded-full p-2 hover:bg-ivory-deep" onClick={() => drawer.current?.close()}><CloseIcon /></button>
        </div>
        <div className="px-5 py-6"><FilterForm facets={facets} idPrefix="m" /></div>
        <div className="sticky bottom-0 border-t border-line bg-ivory p-4">
          <button type="button" className="btn-primary w-full" onClick={() => drawer.current?.close()}>Show {total} {total === 1 ? 'result' : 'results'}</button>
        </div>
      </dialog>
    </>
  );
}
