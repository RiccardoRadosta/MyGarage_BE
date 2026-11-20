-- ==============================================================================
-- Migrazione V8: Timestamp di selezione del veicolo principale post-downgrade
-- ==============================================================================

ALTER TABLE users
ADD COLUMN IF NOT EXISTS primary_vehicle_selected_at TIMESTAMP WITH TIME ZONE;
