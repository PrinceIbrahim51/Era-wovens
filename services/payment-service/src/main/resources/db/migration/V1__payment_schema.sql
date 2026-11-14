-- payment-service owns provider orders, attempts and webhook receipts. No card data is ever stored.
CREATE TABLE provider_orders (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          uuid        NOT NULL,
    provider          varchar(16) NOT NULL DEFAULT 'RAZORPAY_TEST',
    razorpay_order_id varchar(40) NOT NULL UNIQUE,
    amount_paise      bigint      NOT NULL CHECK (amount_paise > 0),
    currency          char(3)     NOT NULL,
    status            varchar(12) NOT NULL CHECK (status IN ('CREATED', 'PAID', 'FAILED')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX provider_orders_order_idx ON provider_orders (order_id);
-- At most one open provider order per order (prevents parallel payment attempts on separate provider orders).
CREATE UNIQUE INDEX provider_orders_one_open_idx ON provider_orders (order_id) WHERE status = 'CREATED';

CREATE TABLE payment_attempts (
    id                  bigserial PRIMARY KEY,
    order_id            uuid        NOT NULL,
    razorpay_order_id   varchar(40) NOT NULL,
    razorpay_payment_id varchar(40),
    status              varchar(24) NOT NULL,
    source              varchar(16) NOT NULL CHECK (source IN ('CHECKOUT', 'WEBHOOK', 'CLIENT_REPORT')),
    method              varchar(20),
    error_code          varchar(60),
    error_description   varchar(300),
    amount_paise        bigint,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX payment_attempts_order_idx ON payment_attempts (order_id, created_at);
CREATE UNIQUE INDEX payment_attempts_captured_once_idx ON payment_attempts (razorpay_payment_id) WHERE status = 'CAPTURED';

CREATE TABLE webhook_receipts (
    event_id       varchar(80) PRIMARY KEY,
    event_type     varchar(60) NOT NULL,
    payload_sha256 char(64)    NOT NULL,
    outcome        varchar(40),
    received_at    timestamptz NOT NULL DEFAULT now(),
    processed_at   timestamptz
);
