-- cart-service owns carts. owner_hash is the gateway-derived SHA-256 of the guest session cookie
-- (later: or of the verified user id). Regular and bulk carts are separate rows.
CREATE TABLE carts (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_hash char(64)    NOT NULL,
    cart_type  varchar(10) NOT NULL CHECK (cart_type IN ('REGULAR', 'BULK')),
    version    bigint      NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    CONSTRAINT one_cart_per_type UNIQUE (owner_hash, cart_type)
);
CREATE INDEX carts_expiry_idx ON carts (expires_at);

CREATE TABLE cart_items (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id    uuid        NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    sku        varchar(40) NOT NULL,
    quantity   int         NOT NULL CHECK (quantity BETWEEN 1 AND 10000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT one_line_per_sku UNIQUE (cart_id, sku)
);
