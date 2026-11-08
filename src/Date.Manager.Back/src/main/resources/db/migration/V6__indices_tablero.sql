-- El tablero cuenta las citas creadas y canceladas en ventanas de tiempo (30 y 60 días) y busca las de las
-- últimas 24 h: sin estos índices cada consulta recorrería toda la tabla.
CREATE INDEX ix_cita_creada_en ON cita (creada_en);
CREATE INDEX ix_cita_cancelada_en ON cita (cancelada_en) WHERE cancelada_en IS NOT NULL;
