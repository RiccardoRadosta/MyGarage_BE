-- ==============================================================================
-- Migrazione V2: Tabella allegati per interventi di manutenzione (PDF e JPEG)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS maintenance_attachments (
    id UUID PRIMARY KEY,
    maintenance_id UUID NOT NULL REFERENCES maintenance_records(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_maintenance_attachments_maintenance_id UNIQUE (maintenance_id)
);

CREATE INDEX IF NOT EXISTS idx_maintenance_attachments_maintenance_id ON maintenance_attachments (maintenance_id);
