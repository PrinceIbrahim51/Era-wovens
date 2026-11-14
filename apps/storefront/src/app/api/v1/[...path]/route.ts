import { NextResponse, type NextRequest } from 'next/server';
import { staticBySlugs } from '@/lib/static-catalog';

/**
 * Only reached in catalogue preview mode. When a gateway is configured, next.config.ts rewrites
 * /api/v1/* to it *before* this route is considered. Ordering endpoints fail closed: nothing about
 * carts, orders or payments is simulated here.
 */
const closed = () =>
  NextResponse.json(
    { status: 503, title: 'Service Unavailable', code: 'ORDERING_NOT_OPEN', detail: 'Ordering isn’t open yet — this is a catalogue preview. Save favourites to your wishlist in the meantime.' },
    { status: 503, headers: { 'Content-Type': 'application/problem+json', 'Cache-Control': 'no-store' } },
  );

export async function GET(req: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const path = (await params).path.join('/');
  if (path === 'catalog/products/batch') {
    const slugs = (req.nextUrl.searchParams.get('slugs') ?? '').split(',').filter((s) => /^[a-z0-9-]{1,120}$/.test(s)).slice(0, 50);
    return NextResponse.json(staticBySlugs(slugs));
  }
  if (path === 'cart/summary') return NextResponse.json({ regularUnits: 0, bulkUnits: 0 });
  if (path === 'payments/config') return NextResponse.json({ onlineEnabled: false, mode: 'test' });
  return closed();
}

export const POST = closed;
export const PATCH = closed;
export const DELETE = closed;
