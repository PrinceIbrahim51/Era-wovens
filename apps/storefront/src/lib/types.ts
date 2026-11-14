// Shapes returned by the API gateway. Money is always integer paise from the server.
export type CartType = 'REGULAR' | 'BULK';

export interface Variant {
  sku: string;
  colorName: string;
  colorFamily: string;
  colorHex: string;
  accentHex: string;
  inStock: boolean | null;
}

export interface Product {
  slug: string;
  name: string;
  collection: { slug: string; name: string };
  fabric: string;
  craft: string;
  occasion: string;
  pricePaise: number;
  bulkPricePaise?: number;
  bulkEligible: boolean;
  displayPricePaise: number;
  motif: Motif;
  isNew: boolean;
  inStock: boolean | null;
  variants: Variant[];
  description?: string;
  maxPerOrder?: number;
}

export type Motif = 'temple' | 'checks' | 'butta' | 'floral' | 'jaal' | 'paisley' | 'stripes' | 'plain';

export interface Collection { slug: string; name: string; description: string; bulk: boolean }

export interface Listing {
  collection?: Collection;
  items: Product[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  facets: {
    colors: { family: string; hex: string; count: number }[];
    fabrics: { value: string; count: number }[];
    minPricePaise: number;
    maxPricePaise: number;
  };
  availabilityKnown: boolean;
}

export interface CartLine {
  itemId: string;
  sku: string;
  productSlug?: string;
  productName: string;
  colorName?: string;
  colorHex?: string;
  accentHex?: string;
  motif?: Motif;
  fabric?: string;
  quantity: number;
  maxQuantity: number;
  unitPricePaise?: number;
  lineTotalPaise?: number;
  issue?: string;
}

export interface Cart {
  cartType: CartType;
  lines: CartLine[];
  lineCount: number;
  units: number;
  subtotalPaise: number;
  bulk?: { minimumUnits: number; units: number; allowMixedSkus: boolean; met: boolean; skusBelowMinimum: string[] };
  readyForCheckout: boolean;
  currency: string;
}

export interface QuoteLine {
  sku: string; productName: string; colorName: string; unitPricePaise: number; quantity: number;
  lineTotalPaise: number; discountPaise: number; taxRateBp: number; taxIncludedPaise: number;
}

export interface Quote {
  quoteId: string;
  expiresAt: string;
  cartType: CartType;
  pincode: string;
  currency: string;
  lines: QuoteLine[];
  subtotalPaise: number;
  discountPaise: number;
  shippingPaise: number;
  taxIncludedPaise: number;
  totalPaise: number;
  coupon?: { code: string; applied: boolean; errorCode?: string; message: string };
  cod: { eligible: boolean; feePaise: number; totalPaise?: number; reasons: string[] };
}

export interface OrderView {
  id: string;
  orderNumber: string;
  status: string;
  paymentMethod: 'COD' | 'ONLINE';
  paymentStatus: string;
  cartType: CartType;
  createdAt: string;
  paymentDueBy?: string;
  items: QuoteLine[];
  totals: { subtotalPaise: number; discountPaise: number; shippingPaise: number; codFeePaise: number; taxIncludedPaise: number; totalPaise: number; currency: string };
  couponCode?: string;
  contactName: string;
  shippingAddress: { line1: string; line2?: string; landmark?: string; city: string; state: string; pincode: string };
  timeline: { status: string; at: string; note: string }[];
}

export interface OrderSummary { id: string; orderNumber: string; status: string; paymentMethod: string; createdAt: string; totalPaise: number; units: number }
