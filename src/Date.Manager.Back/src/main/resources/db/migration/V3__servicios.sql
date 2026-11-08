-- Servicios del negocio y sus ventanas semanales de disponibilidad.
-- valor es dinero: DECIMAL(12,2). dia es ISO (lunes = 1 ... domingo = 7).

CREATE TABLE servicio (
    id           UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_servicio PRIMARY KEY,
    nombre       NVARCHAR(150)    NOT NULL,
    duracion_min INT              NOT NULL CONSTRAINT ck_servicio_duracion CHECK (duracion_min > 0),
    activo       BIT              NOT NULL,
    valor        DECIMAL(12, 2)   NOT NULL CONSTRAINT ck_servicio_valor CHECK (valor >= 0)
);

-- La llave es (servicio_id, id): el id de una ventana lo elige el cliente y solo es único dentro de su servicio.
CREATE TABLE ventana_disponibilidad (
    servicio_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT fk_ventana_servicio REFERENCES servicio (id) ON DELETE CASCADE,
    id          UNIQUEIDENTIFIER NOT NULL,
    dia         INT              NOT NULL CONSTRAINT ck_ventana_dia CHECK (dia BETWEEN 1 AND 7),
    hora_inicio TIME(0)          NOT NULL,
    hora_fin    TIME(0)          NOT NULL,
    CONSTRAINT pk_ventana_disponibilidad PRIMARY KEY (servicio_id, id),
    CONSTRAINT ck_ventana_horas CHECK (hora_fin > hora_inicio)
);
