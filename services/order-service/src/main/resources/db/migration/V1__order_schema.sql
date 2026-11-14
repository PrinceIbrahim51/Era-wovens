-- order-service owns orders, immutable item/price snapshots, the status history and the outbox.
CREATE TABLE orders (
    id                uuid PRIMARY KEY,
    order_number      varchar(20)  NOT NULL UNIQUE,
    idempotency_key   varchar(64)  NOT NULL UNIQUE,
    owner_hash        char(64)     NOT NULL,
    cart_type         varchar(10)  NOT NULL CHECK (cart_type IN ('REGULAR', 'BULK')),
    payment_method    varchar(10)  NOT NULL CHECK (payment_method IN ('COD', 'ONLINE')),
    status            varchar(20)  NOT NULL,
    payment_status    varchar(20)  NOT NULL,
    contact_name      varchar(80)  NOT NULL,
    contact_email     varchar(254) NOT NULL,
    contact_phone     varchar(10)  NOT NULL,
    shipping_address  jsonb        NOT NULL,
    subtotal_paise    bigint       NOT NULL CHECK (subtotal_paise >= 0),
    discount_paise    bigint       NOT NULL CHECK (discount_paise >= 0),
    shipping_paise    bigint       NOT NULL CHECK (shipping_paise >= 0),
    cod_fee_paise     bigint       NOT NULL CHECK (cod_fee_paise >= 0),
    tax_included_paise bigint      NOT NULL CHECK (tax_included_paise >= 0),
    total_paise       bigint       NOT NULL CHECK (total_paise > 0),
    currency          char(3)      NOT NULL,
    coupon_code       varchar(20),
    provider_payment_id varchar(64) UNIQUE,
    version           bigint       NOT NULL DEFAULT 0,
    expires_at        timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT totals_consistent CHECK (total_paise = subtotal_paise - discount_paise + shipping_paise + cod_fee_paise)
);
CREATE INDEX orders_owner_idx ON orders (owner_hash, created_at DESC);
CREATE INDEX orders_expiry_idx ON orders (expires_at) WHERE status IN ('DRAFT', 'PENDING_PAYMENT');

CREATE TABLE order_items (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           uuid         NOT NULL REFERENCES orders (id),
    sku                varchar(40)  NOT NULL,
    product_name       varchar(160) NOT NULL,
    color_name         varchar(60)  NOT NULL,
    unit_price_paise   bigint       NOT NULL CHECK (unit_price_paise > 0),
    quantity           int          NOT NULL CHECK (quantity > 0),
    line_total_paise   bigint       NOT NULL,
    discount_paise     bigint       NOT NULL,
    tax_rate_bp        int          NOT NULL,
    tax_included_paise bigint       NOT NULL,
    CONSTRAINT line_total_consistent CHECK (line_total_paise = unit_price_paise * quantity)
);
CREATE INDEX order_items_order_idx ON order_items (order_id);

-- Snapshots are immutable once written.
CREATE FUNCTION forbid_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'order_items are immutable';
END $$;
CREATE TRIGGER order_items_immutable BEFORE UPDATE OR DELETE ON order_items
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE order_events (
    id          bigserial PRIMARY KEY,
    order_id    uuid        NOT NULL REFERENCES orders (id),
    from_status varchar(20),
    to_status   varchar(20) NOT NULL,
    reason      varchar(200),
    actor       varchar(60) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX order_events_order_idx ON order_events (order_id, id);
CREATE TRIGGER order_events_immutable BEFORE UPDATE OR DELETE ON order_events
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE outbox (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid         NOT NULL,
    event_type   varchar(60)  NOT NULL,
    payload      jsonb        NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    published_at timestamptz,
    attempts     int          NOT NULL DEFAULT 0,
    last_error   varchar(300)
);
CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;
