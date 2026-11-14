const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrExact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });

/** Formats server-provided paise for display only. Never used to compute payable totals. */
export function formatPaise(paise: number, exact = false): string {
  const rupees = paise / 100;
  return exact || paise % 100 !== 0 ? inrExact.format(rupees) : inr.format(rupees);
}

export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Kolkata' }).format(new Date(iso));
}

export const colorLabel = (family: string) => family.charAt(0).toUpperCase() + family.slice(1);

export const ORDER_STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Processing',
  PENDING_PAYMENT: 'Awaiting payment',
  COD_CONFIRMED: 'Cash on delivery accepted',
  CONFIRMED: 'Confirmed',
  FULFILLING: 'Being packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  REVIEW_REQUIRED: 'Under review',
  CANCELLED: 'Cancelled',
  EXPIRED: 'Payment window expired',
  RETURNED: 'Returned',
  REFUNDED: 'Refunded',
};
