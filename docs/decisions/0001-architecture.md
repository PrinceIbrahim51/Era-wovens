# ADR 0001 — Initial architecture choices

**Status:** accepted for M0–M3.

1. **Spring Boot 3.5.x / Spring Cloud 2025.0.x on Java 21.** The specification asks for Boot 3.x; 3.5 is the current 3.x line. A move to Boot 4 is a separate decision.
2. **Spring JDBC (`JdbcClient`) instead of JPA.** Explicit, parameterised SQL keeps reservation and state-transition updates atomic and reviewable, and avoids any temptation to share entity libraries.
3. **Gateway-issued guest session.** The gateway issues a random HttpOnly cookie and forwards only its SHA-256 to services, so a compromised service log or DB never yields a usable session token. Accounts will add a verified user id alongside it.
4. **Same-origin API via Next.js rewrites.** Keeps cookies first-party and lets SameSite=Lax plus an Origin allowlist provide CSRF defence without exposing tokens to JavaScript.
5. **Synchronous orchestration in checkout, events for side effects.** Checkout calls order/inventory/COD directly with idempotent endpoints and compensations; confirmation side effects (notifications, later fulfilment) go through the transactional outbox and RabbitMQ.
6. **Procedural SVG product art.** Guarantees licence-safe imagery for the preview and keeps pages light; to be replaced by real photography via signed uploads.
7. **One thin shared library** (`libs/service-platform`) for security filter, request IDs and error mapping only — no domain types.
