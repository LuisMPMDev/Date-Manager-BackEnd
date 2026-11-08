-- Enlaces de invitación y la llave foránea cita.enlace_id que V4 dejó pendiente.
-- codigo: Base64 URL, distingue mayúsculas; la intercalación binaria hace que el único y la búsqueda comparen byte a
-- byte (la de la base no distingue mayúsculas: 'abc' y 'ABC' serían el mismo código).
-- Instantes en UTC. max_usos NULL = sin límite. usos solo lo cambia el UPDATE condicional de la reserva.
CREATE TABLE enlace_invitacion (
    id             UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_enlace_invitacion PRIMARY KEY,
    codigo         NVARCHAR(43) COLLATE Latin1_General_BIN2 NOT NULL
        CONSTRAINT uq_enlace_invitacion_codigo UNIQUE,
    creado_por     UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT fk_enlace_invitacion_usuario REFERENCES usuario (id),
    creado_en      DATETIME2(3)     NOT NULL,
    vence_en       DATETIME2(3)     NOT NULL,
    max_usos       INT              NULL
        CONSTRAINT ck_enlace_invitacion_max_usos CHECK (max_usos IS NULL OR max_usos >= 1),
    usos           INT              NOT NULL CONSTRAINT df_enlace_invitacion_usos DEFAULT 0
        CONSTRAINT ck_enlace_invitacion_usos CHECK (usos >= 0),
    desactivado_en DATETIME2(3)     NULL,
    CONSTRAINT ck_enlace_invitacion_vence CHECK (vence_en > creado_en),
    -- Red de seguridad: ni un error de la aplicación puede dejar más usos que el máximo.
    CONSTRAINT ck_enlace_invitacion_usos_max CHECK (max_usos IS NULL OR usos <= max_usos)
);

CREATE INDEX ix_enlace_invitacion_creado_por ON enlace_invitacion (creado_por);
CREATE INDEX ix_enlace_invitacion_creado_en ON enlace_invitacion (creado_en DESC, id DESC);

ALTER TABLE cita ADD CONSTRAINT fk_cita_enlace FOREIGN KEY (enlace_id) REFERENCES enlace_invitacion (id);
CREATE INDEX ix_cita_enlace ON cita (enlace_id);
