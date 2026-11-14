-- notification-service owns its inbox (dedupe) and delivery log. Recipients are stored hashed.
CREATE TABLE processed_messages (
    message_id   varchar(64) PRIMARY KEY,
    event_type   varchar(60) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE notification_log (
    id             bigserial PRIMARY KEY,
    message_id     varchar(64) NOT NULL REFERENCES processed_messages (message_id),
    channel        varchar(10) NOT NULL,
    template       varchar(40) NOT NULL,
    recipient_hash char(64)    NOT NULL,
    status         varchar(12) NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);
