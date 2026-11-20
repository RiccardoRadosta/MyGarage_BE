-- ==============================================================================
-- Migrazione V5: Fondamenta Premium, abbonamenti, concessioni e veicolo principale
-- ==============================================================================

-- 1. Ruolo utente (separazione privilegi amministrativi dai benefici Premium)
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 2. Veicolo principale (supporto al veicolo preferito e futuro downgrade)
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE;

-- Indice univoco parziale: al massimo un veicolo principale per ciascun proprietario.
-- Su PostgreSQL viene creato l'indice parziale nativo:
-- CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicles_owner_primary ON vehicles (owner_id) WHERE (is_primary = TRUE);
-- Configurato tramite placeholder Flyway per compatibilità con i test in-memory H2.
${createPrimaryVehicleIndexSql}

-- 3. Tabella abbonamenti acquistati (Google Play Billing / provider esterni)
CREATE TABLE IF NOT EXISTS user_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL DEFAULT 'GOOGLE_PLAY',
    product_id VARCHAR(100) NOT NULL,
    purchase_token VARCHAR(512),
    order_id VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    starts_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    auto_renewing BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_id ON user_subscriptions (user_id);
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_status_expires ON user_subscriptions (user_id, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_purchase_token ON user_subscriptions (purchase_token);

-- 4. Tabella concessioni Premium manuali (Developer, Tester, Collaborator)
CREATE TABLE IF NOT EXISTS premium_grants (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grant_type VARCHAR(30) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_by VARCHAR(100) NOT NULL,
    notes VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_premium_grants_user_id ON premium_grants (user_id);
CREATE INDEX IF NOT EXISTS idx_premium_grants_active ON premium_grants (user_id, revoked_at, expires_at);
