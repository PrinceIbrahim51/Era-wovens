-- checkout-service owns quotes, checkout sessions (idempotency) and coupon redemption.
CREATE TABLE coupons (
    code               varchar(20) PRIMARY KEY CHECK (code ~ '^[A-Z0-9]{3,20}$'),
    description        varchar(200) NOT NULL,
    kind               varchar(10)  NOT NULL CHECK (kind IN ('PERCENT', 'FLAT')),
    value              bigint       NOT NULL CHECK (value > 0),   -- basis points for PERCENT, paise for FLAT
    max_discount_paise bigint,
    min_subtotal_paise bigint       NOT NULL DEFAULT 0,
    applies_to         varchar(10)  NOT NULL CHECK (applies_to IN ('ALL', 'REGULAR', 'BULK')),
    active             boolean      NOT NULL DEFAULT true,
    valid_from         timestamptz  NOT NULL DEFAULT now(),
    valid_to           timestamptz,
    usage_limit        int,
    used_count         int          NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    CONSTRAINT percent_bounds CHECK (kind <> 'PERCENT' OR value <= 10000)
);

CREATE TABLE quotes (
    id            uuid PRIMARY KEY,
    owner_hash    char(64)    NOT NULL,
    cart_type     varchar(10) NOT NULL,
    pincode       char(6)     NOT NULL,
    coupon_code   varchar(20),
    fingerprint   char(64)    NOT NULL,
    payload       jsonb       NOT NULL,
    total_paise   bigint      NOT NULL,
    signature     char(64)    NOT NULL,
    expires_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX quotes_owner_idx ON quotes (owner_hash, created_at DESC);

CREATE TABLE checkout_sessions (
    id              uuid PRIMARY KEY,
    owner_hash      char(64)    NOT NULL,
    idempotency_key varchar(64) NOT NULL,
    request_hash    char(64)    NOT NULL,
    quote_id        uuid        NOT NULL REFERENCES quotes (id),
    payment_method  varchar(10) NOT NULL CHECK (payment_method IN ('COD', 'ONLINE')),
    status          varchar(12) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    order_id        uuid,
    response        jsonb,
    error_code      varchar(40),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT one_session_per_idempotency_key UNIQUE (owner_hash, idempotency_key)
);

CREATE TABLE coupon_redemptions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_code varchar(20) NOT NULL REFERENCES coupons (code),
    session_id  uuid        NOT NULL UNIQUE REFERENCES checkout_sessions (id),
    owner_hash  char(64)    NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
