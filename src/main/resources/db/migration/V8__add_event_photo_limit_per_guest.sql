-- Estilo "câmera descartável": cada convidado (deviceId) tem um número fixo
-- de fotos por evento, definido pelo host na criação. Padrão 10.
ALTER TABLE events ADD COLUMN photo_limit_per_guest INT NOT NULL DEFAULT 10;
