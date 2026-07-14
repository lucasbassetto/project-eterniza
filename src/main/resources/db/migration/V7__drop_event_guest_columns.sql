-- guest_count nunca era incrementado em produção (incrementGuestCount não tinha
-- chamadores) e guest_limit nunca foi aplicado. Convidados são identificados por
-- deviceId, que é forjável: limitar "número de convidados" não é enforceável.
-- O controle real será o limite de fotos por convidado (feature futura).
ALTER TABLE events DROP COLUMN guest_limit;
ALTER TABLE events DROP COLUMN guest_count;
