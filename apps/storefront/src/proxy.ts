import { NextResponse, type NextRequest } from 'next/server';

/**
 * Per-request nonce-based Content-Security-Policy. Next.js applies the nonce to its own scripts;
 * 'strict-dynamic' lets those trusted scripts load Razorpay Checkout. Styles allow inline because
 * React style props and next/font inject inline styles (no script execution risk).
 */
export function proxy(request: NextRequest) {
  const nonce = btoa(crypto.randomUUID());
  const dev = process.env.NODE_ENV !== 'production';
  const csp = [
    `default-src 'self'`,
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic' https://checkout.razorpay.com${dev ? " 'unsafe-eval'" : ''}`,
    `style-src 'self' 'unsafe-inline'`,
    `img-src 'self' data: blob: https://*.razorpay.com`,
    `font-src 'self'`,
    `connect-src 'self' https://*.razorpay.com${dev ? ' ws:' : ''}`,
    `frame-src https://api.razorpay.com https://checkout.razorpay.com`,
    `frame-ancestors 'none'`,
    `form-action 'self'`,
    `base-uri 'self'`,
    `object-src 'none'`,
  ].join('; ');

  const headers = new Headers(request.headers);
  headers.set('x-nonce', nonce);
  headers.set('Content-Security-Policy', csp);
  const response = NextResponse.next({ request: { headers } });
  response.headers.set('Content-Security-Policy', csp);
  return response;
}

export const config = {
  matcher: [{ source: '/((?!api/|_next/static|_next/image|favicon.ico).*)', missing: [{ type: 'header', key: 'next-router-prefetch' }] }],
};
