-- cod-service owns COD eligibility decisions, confirmations, and cash collection reconciliation.
CREATE TABLE cod_confirmations (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          uuid        NOT NULL UNIQUE,
    owner_hash        char(64)    NOT NULL,
    phone_hash        char(64)    NOT NULL,   -- HMAC(phone, pepper); raw phone is not stored here
    pincode           char(6)     NOT NULL,
    order_value_paise bigint      NOT NULL CHECK (order_value_paise > 0),
    fee_paise         bigint      NOT NULL CHECK (fee_paise >= 0),
    status            varchar(20) NOT NULL CHECK (status IN ('PENDING_COLLECTION', 'COLLECTED', 'RTO', 'CANCELLED')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX cod_confirmations_owner_idx ON cod_confirmations (owner_hash, status);
CREATE INDEX cod_confirmations_phone_idx ON cod_confirmations (phone_hash, status, updated_at);

-- Append-only record of cash collected or returned-to-origin, with the staff/courier actor.
CREATE TABLE cod_collections (
    id               bigserial PRIMARY KEY,
    order_id         uuid        NOT NULL REFERENCES cod_confirmations (order_id),
    outcome          varchar(12) NOT NULL CHECK (outcome IN ('COLLECTED', 'RTO', 'CANCELLED')),
    collected_paise  bigint      NOT NULL DEFAULT 0 CHECK (collected_paise >= 0),
    actor            varchar(80) NOT NULL,
    note             varchar(300),
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX cod_collections_one_outcome_idx ON cod_collections (order_id);
