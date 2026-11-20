-- ==============================================================================
-- Migrazione V7: Inizializzazione veicolo principale per utenti con veicolo singolo
-- ==============================================================================

-- Per gli account che possiedono esattamente un veicolo, imposta is_primary = TRUE se non già impostato.
-- Gli account con 0 o più di 1 veicolo non vengono modificati (nessuna scelta arbitraria).
UPDATE vehicles
SET is_primary = TRUE
WHERE is_primary = FALSE
  AND owner_id IN (
      SELECT owner_id
      FROM vehicles
      GROUP BY owner_id
      HAVING COUNT(*) = 1
  );
