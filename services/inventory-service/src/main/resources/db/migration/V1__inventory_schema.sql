-- inventory-service owns stock. Available = on_hand - reserved; constraints make overselling impossible.
CREATE TABLE stock_levels (
    sku        varchar(40) PRIMARY KEY,
    on_hand    int         NOT NULL CHECK (on_hand >= 0),
    reserved   int         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version    bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reserved_within_on_hand CHECK (reserved <= on_hand)
);

CREATE TABLE stock_reservations (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference  varchar(64) NOT NULL,
    sku        varchar(40) NOT NULL REFERENCES stock_levels (sku),
    quantity   int         NOT NULL CHECK (quantity > 0),
    status     varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT one_line_per_sku_per_reference UNIQUE (reference, sku)
);
CREATE INDEX stock_reservations_active_expiry_idx ON stock_reservations (expires_at) WHERE status = 'ACTIVE';

-- Append-only audit trail of every stock change.
CREATE TABLE stock_movements (
    id             bigserial PRIMARY KEY,
    sku            varchar(40) NOT NULL REFERENCES stock_levels (sku),
    delta_on_hand  int         NOT NULL,
    delta_reserved int         NOT NULL,
    reason         varchar(24) NOT NULL,
    reference      varchar(64) NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX stock_movements_sku_idx ON stock_movements (sku, created_at);
