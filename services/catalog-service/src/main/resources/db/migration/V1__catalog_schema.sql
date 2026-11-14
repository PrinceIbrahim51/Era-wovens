-- catalog-service owns collections, products and variants. No other service reads these tables.
CREATE TABLE collections (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        varchar(80)  NOT NULL UNIQUE,
    name        varchar(120) NOT NULL,
    description text         NOT NULL,
    is_bulk     boolean      NOT NULL DEFAULT false,
    sort_order  int          NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             varchar(120) NOT NULL UNIQUE,
    name             varchar(160) NOT NULL,
    description      text         NOT NULL,
    collection_id    uuid         NOT NULL REFERENCES collections (id),
    fabric           varchar(60)  NOT NULL,
    craft            varchar(60)  NOT NULL,
    occasion         varchar(60)  NOT NULL,
    price_paise      bigint       NOT NULL CHECK (price_paise > 0),
    bulk_price_paise bigint       CHECK (bulk_price_paise > 0),
    bulk_eligible    boolean      GENERATED ALWAYS AS (bulk_price_paise IS NOT NULL) STORED,
    motif            varchar(30)  NOT NULL,
    is_new           boolean      NOT NULL DEFAULT false,
    featured_rank    int,
    max_per_order    int          NOT NULL DEFAULT 5 CHECK (max_per_order > 0),
    active           boolean      NOT NULL DEFAULT true,
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    search_vector    tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(fabric, '') || ' ' || coalesce(craft, '') || ' ' || coalesce(occasion, '') || ' ' || coalesce(motif, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'C')
    ) STORED
);
CREATE INDEX products_search_idx ON products USING gin (search_vector);
CREATE INDEX products_collection_idx ON products (collection_id) WHERE active;

CREATE TABLE product_variants (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   uuid        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku          varchar(40) NOT NULL UNIQUE,
    color_name   varchar(60) NOT NULL,
    color_family varchar(30) NOT NULL,
    color_hex    char(7)     NOT NULL CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    accent_hex   char(7)     NOT NULL CHECK (accent_hex ~ '^#[0-9A-Fa-f]{6}$'),
    position     int         NOT NULL DEFAULT 0,
    active       boolean     NOT NULL DEFAULT true
);
CREATE INDEX product_variants_product_idx ON product_variants (product_id);
CREATE INDEX product_variants_family_idx ON product_variants (color_family);
