-- ==============================================================================
-- Schema iniziale Il Mio Garage
-- ==============================================================================

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(512) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    icon VARCHAR(30) NOT NULL,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(100) NOT NULL,
    "year" INTEGER NOT NULL,
    plate VARCHAR(30) NOT NULL,
    mileage INTEGER NOT NULL CHECK (mileage >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_vehicles_owner_plate UNIQUE (owner_id, plate)
);

CREATE INDEX IF NOT EXISTS idx_vehicles_owner_id ON vehicles (owner_id);

CREATE TABLE IF NOT EXISTS maintenance_records (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    custom_description VARCHAR(100),
    date DATE NOT NULL,
    mileage INTEGER NOT NULL CHECK (mileage >= 0),
    cost NUMERIC(10, 2) NOT NULL CHECK (cost >= 0),
    notes VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_maintenance_owner_vehicle_date ON maintenance_records (owner_id, vehicle_id, date DESC);
CREATE INDEX IF NOT EXISTS idx_maintenance_owner_date ON maintenance_records (owner_id, date DESC);
