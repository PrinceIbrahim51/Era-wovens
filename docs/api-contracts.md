# API contracts (implemented)

Public endpoints are reached only through the gateway under `/api/v1`. Errors are RFC 9457 problem details with a stable `code`. Money is integer paise. Unknown JSON fields are rejected.

## Public (via gateway)

| Method | Path | Notes |
|---|---|---|
| GET | `/catalog/collections` | cacheable |
| GET | `/catalog/products?collection=&color=a,b&fabric=&minPrice=&maxPrice=&inStock=&q=&sort=featured\|newest\|price_asc\|price_desc\|name_asc&page=&size=` | rupees in price filters; returns items, facets, `availabilityKnown` |
| GET | `/catalog/products/{slug}` | detail + related |
| GET | `/catalog/products/batch?slugs=a,b` | ≤ 50, for wishlists |
| GET | `/cart?type=REGULAR\|BULK` | preview prices, line issues, bulk status |
| GET | `/cart/summary` | unit counts for both carts |
| POST | `/cart/items` `{sku, quantity, cartType}` | validates SKU, bulk eligibility, limits, stock |
| PATCH | `/cart/items/{itemId}` `{quantity}` | owner-checked |
| DELETE | `/cart/items/{itemId}` | owner-checked |
| POST | `/checkout/quotes` `{cartType, pincode, couponCode?}` | signed quote with online and COD totals |
| POST | `/checkout/sessions` + `Idempotency-Key` `{quoteId, paymentMethod, contact, shippingAddress}` | 201; errors: `QUOTE_CHANGED` (body has `quote`), `QUOTE_EXPIRED`, `INSUFFICIENT_STOCK`, `COD_NOT_ELIGIBLE`, `IDEMPOTENCY_KEY_REUSED` |
| GET | `/orders/{id}` | owner only (404 otherwise) |
| GET | `/me/orders` | orders for this browser session |
| POST | `/orders/lookup` `{orderNumber, email}` | masked address; rate-limited |
| POST | `/cod/eligibility` `{pincode, cartType}` | advisory only |
| GET | `/payments/config` | `{onlineEnabled, mode:"test"}` |
| POST | `/payments/razorpay/orders` `{orderId}` | returns public test key id + Razorpay order id |
| POST | `/payments/razorpay/verify` `{orderId, razorpayOrderId, razorpayPaymentId, razorpaySignature}` | `CAPTURED` / `PENDING` / `FAILED` |
| POST | `/payments/razorpay/failure` | client report, informational |
| POST | `/webhooks/razorpay` | raw body, `X-Razorpay-Signature`, `X-Razorpay-Event-Id` |

## Internal (service token; never routed by the gateway)

| Owner | Endpoint |
|---|---|
| catalog | `POST /internal/v1/catalog/skus/lookup` |
| inventory | `POST /internal/v1/inventory/availability`, `POST /internal/v1/inventory/reservations`, `GET …/reservations/{ref}`, `POST …/{ref}/commit`, `POST …/{ref}/release` |
| cart | `GET /internal/v1/carts/{ownerHash}?type=`, `POST …/consume` |
| order | `POST /internal/v1/orders`, `GET …/{id}`, `POST …/{id}/transitions`, `POST …/{id}/payment-captured` |
| cod | `POST /internal/v1/cod/eligibility`, `POST …/confirmations`, `POST …/confirmations/{orderId}/outcome` |

## Events (RabbitMQ topic exchange `aurelia.orders`)

`order.confirmed`, `order.cancelled`, `order.expired`, `order.review_required` — message id = outbox id (consumers dedupe on it). Payload: `orderId, orderNumber, status, paymentMethod, totalPaise, currency, contactName, contactEmail, occurredAt`.

OpenAPI documents will be generated from these controllers in a later milestone.
