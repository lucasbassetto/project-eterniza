-- photo_count nunca era incrementado (ficava sempre 0) e agora é calculado a
-- partir da tabela photos, que é a fonte da verdade. Manter a coluna só criaria
-- risco de deriva entre o contador e as fotos reais.
ALTER TABLE events DROP COLUMN photo_count;
