-- Clientes (identificados por correo) y citas.
-- cliente.correo se guarda normalizado (trim + minúsculas) por la aplicación; el único evita duplicados aunque
-- dos reservas con el mismo correo nuevo lleguen a la vez.
CREATE TABLE cliente (
    id       UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_cliente PRIMARY KEY,
    nombre   NVARCHAR(100)    NOT NULL,
    apellido NVARCHAR(100)    NOT NULL,
    telefono NVARCHAR(40)     NOT NULL,
    correo   NVARCHAR(254)    NOT NULL CONSTRAINT uq_cliente_correo UNIQUE
);

-- fecha, hora_inicio y hora_fin son hora local del negocio (sin zona); creada_en y cancelada_en son UTC.
-- La cita guarda su fin: cambiar la duración del servicio después no modifica citas hechas.
-- enlace_id: enlace de invitación con que se reservó (la llave foránea la añade la migración de enlaces).
CREATE TABLE cita (
    id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_cita PRIMARY KEY,
    servicio_id  UNIQUEIDENTIFIER NOT NULL CONSTRAINT fk_cita_servicio REFERENCES servicio (id),
    cliente_id   UNIQUEIDENTIFIER NOT NULL CONSTRAINT fk_cita_cliente REFERENCES cliente (id),
    fecha        DATE             NOT NULL,
    hora_inicio  TIME(0)          NOT NULL,
    hora_fin     TIME(0)          NOT NULL,
    estado       NVARCHAR(20)     NOT NULL
        CONSTRAINT ck_cita_estado CHECK (estado IN ('Confirmada', 'Atendida', 'Cancelada')),
    creada_en    DATETIME2(3)     NOT NULL,
    cancelada_en DATETIME2(3)     NULL,
    enlace_id    UNIQUEIDENTIFIER NULL,
    CONSTRAINT ck_cita_horas CHECK (hora_fin > hora_inicio)
);

-- Red de seguridad contra el doble agendamiento exacto: dos citas no canceladas del mismo servicio no pueden
-- empezar a la misma hora. Los solapamientos parciales los impide la aplicación bajo el bloqueo del servicio.
CREATE UNIQUE INDEX uq_cita_servicio_inicio ON cita (servicio_id, fecha, hora_inicio) WHERE estado <> 'Cancelada';

CREATE INDEX ix_cita_fecha ON cita (fecha, hora_inicio);
CREATE INDEX ix_cita_cliente ON cita (cliente_id);
