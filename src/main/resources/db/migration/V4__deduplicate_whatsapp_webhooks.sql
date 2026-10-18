CREATE TABLE processed_whatsapp_messages (
    message_id VARCHAR(160) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
