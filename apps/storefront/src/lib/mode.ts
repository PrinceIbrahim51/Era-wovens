import 'server-only';

/**
 * Catalogue preview mode: used when no backend gateway is configured (e.g. a Vercel-only deployment).
 * The catalogue is served from the bundled seed data and ordering is closed — no carts, orders or
 * payments are simulated.
 */
export const isPreviewMode = () => process.env.STOREFRONT_MODE === 'preview' || !process.env.GATEWAY_URL;
