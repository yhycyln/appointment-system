CREATE TABLE employees (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Istanbul',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    phone VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customer_channel_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    channel VARCHAR(20) NOT NULL,
    external_user_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_channel_external UNIQUE (channel, external_user_id),
    CONSTRAINT ck_channel CHECK (channel IN ('TELEGRAM', 'WHATSAPP'))
);

CREATE TABLE weekly_working_hours (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_minute INTEGER NOT NULL CHECK (start_minute >= 0),
    end_minute INTEGER NOT NULL CHECK (end_minute <= 1440),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (start_minute < end_minute)
);

CREATE TABLE breaks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_minute INTEGER NOT NULL CHECK (start_minute >= 0),
    end_minute INTEGER NOT NULL CHECK (end_minute <= 1440),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (start_minute < end_minute)
);

CREATE TABLE blocked_periods (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_at < end_at)
);

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    CONSTRAINT ck_appointment_time CHECK (start_at < end_at),
    CONSTRAINT ck_appointment_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'))
);

-- PENDING appointments reserve the slot while waiting for employee approval.
-- Adjacent slots are allowed because the range uses [) bounds.
ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
    EXCLUDE USING gist (
        employee_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING', 'CONFIRMED'));

CREATE INDEX idx_appointments_employee_start
    ON appointments(employee_id, start_at);

CREATE INDEX idx_appointments_status_start
    ON appointments(status, start_at);

CREATE INDEX idx_blocked_periods_employee_start
    ON blocked_periods(employee_id, start_at);

CREATE TABLE telegram_sessions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    telegram_user_id VARCHAR(128) NOT NULL UNIQUE,
    state VARCHAR(40) NOT NULL DEFAULT 'START',
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE booking_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    slot_duration_minutes INTEGER NOT NULL DEFAULT 40 CHECK (slot_duration_minutes > 0),
    booking_horizon_days INTEGER NOT NULL DEFAULT 7 CHECK (booking_horizon_days > 0),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Istanbul'
);

INSERT INTO booking_settings (id, slot_duration_minutes, booking_horizon_days, timezone)
VALUES (1, 40, 7, 'Europe/Istanbul');
