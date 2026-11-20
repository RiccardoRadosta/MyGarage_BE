-- ==============================================================================
-- Migrazione V6: Indice univoco parziale per (provider, purchase_token)
-- ==============================================================================

-- Drop dell'indice non univoco precedente su purchase_token (se presente)
DROP INDEX IF EXISTS idx_user_subscriptions_purchase_token;

-- Indice univoco per (provider, purchase_token) per i token non nulli.
-- Su PostgreSQL viene applicato l'indice parziale nativo:
-- CREATE UNIQUE INDEX IF NOT EXISTS uq_user_subscriptions_provider_token ON user_subscriptions (provider, purchase_token) WHERE (purchase_token IS NOT NULL);
-- Su H2 (test in-memory) l'indice univoco standard gestisce nativamente i NULL distinti.
${createPurchaseTokenIndexSql}
