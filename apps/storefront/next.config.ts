import type { NextConfig } from 'next';

const gateway = process.env.GATEWAY_URL;

const config: NextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  // The browser talks to same-origin /api/v1/*; Next proxies to the gateway, keeping the guest cookie first-party.
  // Without GATEWAY_URL the site runs in catalogue preview mode (see src/lib/mode.ts).
  async rewrites() {
    return gateway ? { beforeFiles: [{ source: '/api/v1/:path*', destination: `${gateway}/api/v1/:path*` }], afterFiles: [], fallback: [] } : [];
  },
  async headers() {
    return [{
      source: '/:path*',
      headers: [
        { key: 'X-Content-Type-Options', value: 'nosniff' },
        { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
        { key: 'X-Frame-Options', value: 'DENY' },
        { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=(), payment=(self "https://api.razorpay.com")' },
        { key: 'Cross-Origin-Opener-Policy', value: 'same-origin-allow-popups' },
        ...(process.env.SECURE_COOKIES === 'true' ? [{ key: 'Strict-Transport-Security', value: 'max-age=31536000; includeSubDomains' }] : []),
      ],
    }];
  },
};

export default config;
