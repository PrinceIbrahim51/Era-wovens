export const LINE_ISSUES: Record<string, string> = {
  SKU_UNAVAILABLE: 'No longer available — please remove.',
  NOT_BULK_ELIGIBLE: 'Not part of the bulk collection.',
  QUANTITY_LIMIT_EXCEEDED: 'Over the per-order limit — reduce the quantity.',
  OUT_OF_STOCK: 'Out of stock — please remove.',
  INSUFFICIENT_STOCK: 'Only limited stock left — reduce the quantity.',
};

export const COD_REASONS: Record<string, string> = {
  PINCODE_NOT_ELIGIBLE: 'COD is not offered for this PIN code.',
  BULK_ORDERS_NOT_ELIGIBLE: 'COD is not available for bulk orders.',
  ORDER_VALUE_ABOVE_LIMIT: 'COD is available on orders up to ₹25,000.',
  TOO_MANY_PENDING_COD_ORDERS: 'You have several COD orders awaiting delivery.',
  RECENT_RETURNS_TO_ORIGIN: 'COD is unavailable due to recent undelivered orders.',
  COD_TEMPORARILY_UNAVAILABLE: 'COD is temporarily unavailable.',
};
