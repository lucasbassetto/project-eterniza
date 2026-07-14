-- O filtro é aplicado no app (client-side) e o convidado escolhe livremente o
-- seu; o "filtro do evento" não é mais usado pelo servidor. A coluna era apenas
-- metadado inerte (armazenado e devolvido, sem efeito), então é removida.
ALTER TABLE events DROP COLUMN film_style;
