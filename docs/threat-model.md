# Threat model (M3 slice)

Scope: storefront, gateway, catalog, inventory, cart, checkout, order, COD, payment, notification. Accounts and admin are out of scope until they exist and must extend this document.

## Trust boundaries

1. **Browser → storefront (Next.js)** — untrusted input. Same-origin `/api/v1/*` is rewritten to the gateway.
2. **Storefront → gateway** — the gateway is the only public backend entry point. It trusts one proxy hop of `X-Forwarded-For` (configurable).
3. **Gateway → services** — authenticated with `X-Internal-Token` (gateway role). Client-supplied `X-Internal-Token`, `X-Guest-Session` and `Cookie` headers are stripped.
4. **Service → service** — `/internal/**` with the service-role token. Not routable from the gateway.
5. **Razorpay → payment-service** — webhooks, authenticated only by HMAC over the raw body.
6. **Services → PostgreSQL / Redis / RabbitMQ** — one DB role per service, ports bound to localhost.

## Threats and controls

| Threat | Control | Status / test |
|---|---|---|
| Price/total tampering | Client never sends prices; checkout recomputes from catalog; order-service re-validates snapshot arithmetic; DB check constraints | `PricingEngineTest`, `OrderServiceIntegrationTest`, manual |
| Stale or forged quote | Server-stored quote, HMAC signature, 15-min expiry, re-price before placement (`QUOTE_CHANGED`) | implemented |
| Duplicate orders / double charge | `Idempotency-Key` per owner + request hash; unique idempotency key on orders; one open provider order per order; unique captured payment id; `ALREADY_PAID` guard | integration tests + manual replay |
| Overselling | Atomic conditional `UPDATE … WHERE on_hand - reserved >= qty`, all-or-nothing, TTL + sweeper | 25-thread concurrency test |
| Spoofed payment success | Signature verification + authoritative payment fetch; webhook HMAC (separate secret, raw bytes); amount/currency/order match; never trust browser callback alone | `PaymentServiceIntegrationTest`, `SignaturesTest` |
| Webhook replay / out-of-order | Event-id dedupe table; idempotent capture; late capture → `REVIEW_REQUIRED` | tests |
| Live money by accident | Startup refuses `mode != test` and non-`rzp_test_` keys | `RazorpayPropertiesTest` |
| IDOR / BOLA | Ownership via gateway-derived session hash; non-owned resources return 404; cart items checked against owner | manual + code review |
| CSRF | SameSite=Lax HttpOnly cookie + exact Origin/Referer allowlist on unsafe methods at the gateway (webhook exempt, HMAC-protected) | `EdgeFilterTest` |
| XSS | React escaping, no raw HTML except escaped JSON-LD; nonce CSP with `strict-dynamic`; `frame-ancestors 'none'` | build + header check |
| Mass assignment | `FAIL_ON_UNKNOWN_PROPERTIES`, explicit DTO records, Bean Validation | manual (unknown field → 400) |
| SQL injection | Parameterised `JdbcClient` everywhere; LIKE input escaped; FTS via `websearch_to_tsquery` | code review |
| Credential/secret leakage | No secrets in repo; `.env` gitignored and dockerignored; fail-closed config validation; secrets redacted from `toString`; Gitleaks in CI | CI |
| PII in logs | Emails/phones masked; COD stores phone HMAC; notification log stores recipient hash | code review |
| Abuse / brute force | Redis rate limits per IP and route (checkout, payments, order lookup stricter); COD pending/RTO limits | config |
| Internal API exposure | Default-deny security; gateway routes are an allowlist; actuator exposes health only | manual 401/404 |

## Residual risks (must be closed before production)

* Shared internal tokens instead of mTLS/workload identity; no network policies yet.
* No customer authentication, admin MFA, RBAC or audit UI (M2/M4).
* Rate limiter fails open if Redis is down (Spring Cloud Gateway default).
* No DAST (OWASP ZAP), penetration test, load test or backup/restore drill performed.
* Order PII (name, email, phone, address) is protected by storage encryption only; field-level encryption and retention policies are pending legal input.
* Images are inline SVG; when real media uploads arrive, signed uploads, type sniffing and malware scanning are required.
