-- O filtro passou a ser aplicado no app (client-side); a foto é armazenada já
-- finalizada, sem processamento no servidor. O único estado agora é READY, então
-- o default 'PROCESSING' herdado da V3 não faz mais sentido.
ALTER TABLE photos ALTER COLUMN status SET DEFAULT 'READY';
