-- =====================================================================================================
-- DateManager: script para crear la base de datos completa en SQL Server (tablas, restricciones,
-- índices y disparadores).
--
-- Es la suma, en orden, de las migraciones V1..V10 de src/main/resources/db/migration (esa carpeta es la
-- fuente de verdad: si se añade una migración, hay que añadirla también aquí).
--
-- Cómo usarlo
--   * SSMS / Azure Data Studio: abre el archivo (UTF-8) y ejecútalo conectado con un login que pueda crear
--     bases de datos.
--   * sqlcmd:  sqlcmd -S localhost -U <usuario> -P <clave> -f 65001 -b -i crear-base-de-datos.sql
--
-- Qué hace
--   1. Crea la base DateManager si no existe.
--   2. Se detiene sin tocar nada si la base ya tiene tablas de DateManager (nunca borra ni modifica datos).
--   3. Crea el esquema (V1..V10).
--   4. Registra V1..V10 en la tabla historial_migraciones con sus sumas de verificación, para que la API
--      al arrancar reconozca el esquema como ya migrado y no lo vuelva a aplicar.
--
-- Para otra base (por ejemplo DateManagerTest) reemplaza DateManager en las líneas marcadas con [NOMBRE].
-- La base solo debe ser creada una vez; los datos de ejemplo no se cargan aquí (los siembra la API con
-- DATEMANAGER_PERFIL=dev si la base está vacía).
-- =====================================================================================================
SET NOCOUNT ON;
GO

IF DB_ID(N'DateManager') IS NULL          -- [NOMBRE]
    CREATE DATABASE [DateManager];        -- [NOMBRE]
GO

USE [DateManager];                        -- [NOMBRE]
GO

-- Protección: si ya hay tablas de DateManager no se ejecuta nada de lo que sigue.
IF OBJECT_ID(N'dbo.cita', N'U') IS NOT NULL
   OR OBJECT_ID(N'dbo.usuario', N'U') IS NOT NULL
   OR OBJECT_ID(N'dbo.historial_migraciones', N'U') IS NOT NULL
   OR OBJECT_ID(N'dbo.flyway_schema_history', N'U') IS NOT NULL
BEGIN
    RAISERROR(N'La base ya tiene tablas de DateManager: no se ejecuta nada. Usa una base vacía.', 16, 1);
    SET NOEXEC ON;
END
GO

CREATE TABLE historial_migraciones (
    version      INT           NOT NULL CONSTRAINT pk_historial_migraciones PRIMARY KEY,
    description  NVARCHAR(200) NOT NULL,
    checksum     INT           NOT NULL,
    installed_on DATETIME2(3)  NOT NULL,
    success      BIT           NOT NULL
);
GO

-- ----------------------------------------------------------------------------------------------------
-- V1: base  (V1__base.sql)
-- ----------------------------------------------------------------------------------------------------
-- Línea base vacía: confirma que Flyway se conecta y migra.
-- El esquema real llega en las migraciones siguientes.
GO

-- ----------------------------------------------------------------------------------------------------
-- V2: roles usuarios  (V2__roles_usuarios.sql)
-- ----------------------------------------------------------------------------------------------------
-- Roles, sus permisos (ids snake_case del catálogo) y usuarios del panel.

CREATE TABLE rol (
    id          UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_rol PRIMARY KEY,
    nombre      NVARCHAR(100)    NOT NULL,
    descripcion NVARCHAR(300)    NOT NULL
);

CREATE TABLE rol_permiso (
    rol_id  UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT fk_rol_permiso_rol REFERENCES rol (id) ON DELETE CASCADE,
    permiso NVARCHAR(40)     NOT NULL
        CONSTRAINT ck_rol_permiso_permiso CHECK (permiso IN (
            'ver_panel', 'ver_citas', 'gestionar_citas', 'atender_citas', 'gestionar_servicios',
            'gestionar_clientes', 'gestionar_usuarios', 'gestionar_roles', 'gestionar_enlaces')),
    CONSTRAINT pk_rol_permiso PRIMARY KEY (rol_id, permiso)
);

-- usuario.usuario se guarda normalizado (trim + minúsculas) por la aplicación; el único lo protege
-- además de duplicados. clave_hash guarda solo el hash BCrypt, nunca la contraseña.
CREATE TABLE usuario (
    id         UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_usuario PRIMARY KEY,
    nombre     NVARCHAR(100)    NOT NULL,
    apellido   NVARCHAR(100)    NOT NULL,
    correo     NVARCHAR(254)    NOT NULL,
    telefono   NVARCHAR(40)     NOT NULL,
    usuario    NVARCHAR(100)    NOT NULL CONSTRAINT uq_usuario_usuario UNIQUE,
    clave_hash NVARCHAR(100)    NOT NULL,
    rol_id     UNIQUEIDENTIFIER NOT NULL CONSTRAINT fk_usuario_rol REFERENCES rol (id)
);

CREATE INDEX ix_usuario_rol ON usuario (rol_id);
GO

-- ----------------------------------------------------------------------------------------------------
-- V3: servicios  (V3__servicios.sql)
-- ----------------------------------------------------------------------------------------------------
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
GO

-- ----------------------------------------------------------------------------------------------------
-- V4: clientes citas  (V4__clientes_citas.sql)
-- ----------------------------------------------------------------------------------------------------
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
GO

-- ----------------------------------------------------------------------------------------------------
-- V5: enlaces  (V5__enlaces.sql)
-- ----------------------------------------------------------------------------------------------------
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
GO

-- ----------------------------------------------------------------------------------------------------
-- V6: indices tablero  (V6__indices_tablero.sql)
-- ----------------------------------------------------------------------------------------------------
-- El tablero cuenta las citas creadas y canceladas en ventanas de tiempo (30 y 60 días) y busca las de las
-- últimas 24 h: sin estos índices cada consulta recorrería toda la tabla.
CREATE INDEX ix_cita_creada_en ON cita (creada_en);
CREATE INDEX ix_cita_cancelada_en ON cita (cancelada_en) WHERE cancelada_en IS NOT NULL;
GO

-- ----------------------------------------------------------------------------------------------------
-- V7: restricciones de datos  (V7__restricciones_de_datos.sql)
-- ----------------------------------------------------------------------------------------------------
-- Restricciones declarativas: la base rechaza por sí misma los datos que la aplicación ya no admite.
--
-- 1) Auditoría previa: por cada regla se buscan las filas que la violarían. Si hay alguna, la migración aborta con
--    THROW 50001 y un mensaje con el nombre de la regla, la tabla y hasta 5 ids. La auditoría solo lee: nunca
--    corrige ni borra datos. Cada consulta usa NOT (condición) igual que la restricción, así que acepta y rechaza
--    exactamente lo mismo (un CHECK deja pasar el resultado desconocido, y NOT desconocido no selecciona la fila).
-- 2) Si todo está limpio, se añaden las restricciones validadas (WITH CHECK). Flyway ejecuta el script entero en una
--    sola transacción: un fallo no deja restricciones a medias.
--
-- Lo que la aplicación guarda y por eso estas reglas NO rechazan:
--   * usuario.apellido y usuario.correo vacíos: AdministradorInicial crea al administrador sin ellos, así que
--     usuario.apellido no tiene regla de «no vacío» y usuario.correo puede ser vacío (si no lo es, debe tener forma).
--   * usuario.telefono y rol.descripcion vacíos: la aplicación los permite.
--   * Espacios dentro de usuario.usuario: la aplicación solo recorta los extremos; la regla solo exige que no
--     empiece ni termine en espacio.
--   * «Minúsculas» = sin letras A-Z ASCII (comparación binaria): la aplicación normaliza con toLowerCase y acepta
--     tildes, eñes y otros caracteres Unicode en correos y usuarios.

-- ================================================================== Auditoría previa

-- cita: cancelada_en solo si el estado es Cancelada
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM cita
          WHERE NOT ((estado = N'Cancelada' AND cancelada_en IS NOT NULL)
                  OR (estado <> N'Cancelada' AND cancelada_en IS NULL)) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla cita tiene filas que violan la regla ' +
        N'ck_cita_cancelada_coherente (cancelada_en debe tener valor si y solo si el estado es Cancelada). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- cita: cancelada_en no puede ser anterior a creada_en
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM cita
          WHERE NOT (cancelada_en IS NULL OR cancelada_en >= creada_en) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla cita tiene filas que violan la regla ' +
        N'ck_cita_cancelada_posterior (cancelada_en no puede ser anterior a creada_en). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- cliente: nombre
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM cliente WHERE NOT (LEN(LTRIM(RTRIM(nombre))) > 0) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla cliente tiene filas que violan la regla ' +
        N'ck_cliente_nombre_no_vacio (el nombre no puede ser vacío ni solo espacios). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- cliente: apellido
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM cliente WHERE NOT (LEN(LTRIM(RTRIM(apellido))) > 0) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla cliente tiene filas que violan la regla ' +
        N'ck_cliente_apellido_no_vacio (el apellido no puede ser vacío ni solo espacios). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- cliente: teléfono
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM cliente WHERE NOT (LEN(LTRIM(RTRIM(telefono))) > 0) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla cliente tiene filas que violan la regla ' +
        N'ck_cliente_telefono_no_vacio (el teléfono no puede ser vacío ni solo espacios). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- cliente: forma del correo
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM cliente
          WHERE NOT (correo NOT LIKE N'%[ ]%'
                 AND LEN(correo) - LEN(REPLACE(correo, N'@', N'')) = 1
                 AND CHARINDEX(N'@', correo) > 1
                 AND SUBSTRING(correo, CHARINDEX(N'@', correo) + 1, 254) LIKE N'[^.]%.%[^.]'
                 AND correo COLLATE Latin1_General_BIN2 NOT LIKE N'%[A-Z]%') ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla cliente tiene filas que violan la regla ' +
        N'ck_cliente_correo_forma (el correo debe tener una sola @, un punto en el dominio, sin espacios ni ' +
        N'letras mayúsculas A-Z). Ids de ejemplo (hasta 5): ', @ids,
        N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- usuario: nombre
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM usuario WHERE NOT (LEN(LTRIM(RTRIM(nombre))) > 0) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla usuario tiene filas que violan la regla ' +
        N'ck_usuario_nombre_no_vacio (el nombre no puede ser vacío ni solo espacios). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- usuario: forma del correo (vacío permitido: el administrador inicial se crea sin correo)
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM usuario
          WHERE NOT (DATALENGTH(correo) = 0
                 OR (correo NOT LIKE N'%[ ]%'
                 AND LEN(correo) - LEN(REPLACE(correo, N'@', N'')) = 1
                 AND CHARINDEX(N'@', correo) > 1
                 AND SUBSTRING(correo, CHARINDEX(N'@', correo) + 1, 254) LIKE N'[^.]%.%[^.]'
                 AND correo COLLATE Latin1_General_BIN2 NOT LIKE N'%[A-Z]%')) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla usuario tiene filas que violan la regla ' +
        N'ck_usuario_correo_forma (el correo, si lo hay, debe tener una sola @, un punto en el dominio, sin ' +
        N'espacios ni letras mayúsculas A-Z). Ids de ejemplo (hasta 5): ', @ids,
        N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- usuario: forma del nombre de usuario
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM usuario
          WHERE NOT (LEN(LTRIM(RTRIM(usuario))) > 0
                 AND usuario NOT LIKE N'[ ]%'
                 AND usuario NOT LIKE N'%[ ]'
                 AND usuario COLLATE Latin1_General_BIN2 NOT LIKE N'%[A-Z]%') ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla usuario tiene filas que violan la regla ' +
        N'ck_usuario_usuario_forma (el nombre de usuario no puede ser vacío, empezar o terminar en espacio ni ' +
        N'tener letras mayúsculas A-Z). Ids de ejemplo (hasta 5): ', @ids,
        N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- usuario: forma del hash BCrypt
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM usuario
          WHERE NOT (clave_hash COLLATE Latin1_General_BIN2
                 LIKE N'$2[aby]$[0-9][0-9]$' + REPLICATE(N'[./A-Za-z0-9]', 53)) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla usuario tiene filas que violan la regla ' +
        N'ck_usuario_clave_hash_bcrypt (clave_hash debe ser un hash BCrypt de 60 caracteres). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- servicio: nombre
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM servicio WHERE NOT (LEN(LTRIM(RTRIM(nombre))) > 0) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla servicio tiene filas que violan la regla ' +
        N'ck_servicio_nombre_no_vacio (el nombre no puede ser vacío ni solo espacios). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- servicio: duración máxima
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM servicio WHERE NOT (duracion_min <= 1440) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla servicio tiene filas que violan la regla ' +
        N'ck_servicio_duracion_max (la duración no puede superar 1440 minutos). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- rol: nombre
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM rol WHERE NOT (LEN(LTRIM(RTRIM(nombre))) > 0) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla rol tiene filas que violan la regla ' +
        N'ck_rol_nombre_no_vacio (el nombre no puede ser vacío ni solo espacios). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- enlace_invitacion: desactivado_en no puede ser anterior a creado_en
DECLARE @ids NVARCHAR(300) = (SELECT STRING_AGG(CONVERT(NVARCHAR(36), id), N', ')
    FROM (SELECT TOP (5) id FROM enlace_invitacion
          WHERE NOT (desactivado_en IS NULL OR desactivado_en >= creado_en) ORDER BY id) AS v);
IF @ids IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V7 abortada: la tabla enlace_invitacion tiene filas que ' +
        N'violan la regla ck_enlace_invitacion_desactivado (desactivado_en no puede ser anterior a creado_en). ' +
        N'Ids de ejemplo (hasta 5): ', @ids, N'. No se modificó ningún dato: corrige esas filas y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- ================================================================== Restricciones

-- Citas: coherencia entre el estado y la fecha de cancelación.
ALTER TABLE cita WITH CHECK ADD CONSTRAINT ck_cita_cancelada_coherente CHECK (
    (estado = N'Cancelada' AND cancelada_en IS NOT NULL) OR (estado <> N'Cancelada' AND cancelada_en IS NULL));
ALTER TABLE cita WITH CHECK ADD CONSTRAINT ck_cita_cancelada_posterior CHECK (
    cancelada_en IS NULL OR cancelada_en >= creada_en);

-- Textos obligatorios: ni vacíos ni solo espacios (la aplicación recorta y exige @NotBlank).
ALTER TABLE cliente WITH CHECK ADD CONSTRAINT ck_cliente_nombre_no_vacio CHECK (LEN(LTRIM(RTRIM(nombre))) > 0);
ALTER TABLE cliente WITH CHECK ADD CONSTRAINT ck_cliente_apellido_no_vacio CHECK (LEN(LTRIM(RTRIM(apellido))) > 0);
ALTER TABLE cliente WITH CHECK ADD CONSTRAINT ck_cliente_telefono_no_vacio CHECK (LEN(LTRIM(RTRIM(telefono))) > 0);
ALTER TABLE usuario WITH CHECK ADD CONSTRAINT ck_usuario_nombre_no_vacio CHECK (LEN(LTRIM(RTRIM(nombre))) > 0);
ALTER TABLE servicio WITH CHECK ADD CONSTRAINT ck_servicio_nombre_no_vacio CHECK (LEN(LTRIM(RTRIM(nombre))) > 0);
ALTER TABLE rol WITH CHECK ADD CONSTRAINT ck_rol_nombre_no_vacio CHECK (LEN(LTRIM(RTRIM(nombre))) > 0);

-- Correos: una sola @, un punto en el dominio, sin espacios y sin A-Z ASCII (la aplicación guarda trim + minúsculas;
-- la comparación binaria evita rechazar tildes y eñes, que LOWER() de la intercalación trataría distinto).
-- Algo más laxa que la regla de Java, nunca más estricta.
ALTER TABLE cliente WITH CHECK ADD CONSTRAINT ck_cliente_correo_forma CHECK (
    correo NOT LIKE N'%[ ]%'
    AND LEN(correo) - LEN(REPLACE(correo, N'@', N'')) = 1
    AND CHARINDEX(N'@', correo) > 1
    AND SUBSTRING(correo, CHARINDEX(N'@', correo) + 1, 254) LIKE N'[^.]%.%[^.]'
    AND correo COLLATE Latin1_General_BIN2 NOT LIKE N'%[A-Z]%');
ALTER TABLE usuario WITH CHECK ADD CONSTRAINT ck_usuario_correo_forma CHECK (
    DATALENGTH(correo) = 0
    OR (correo NOT LIKE N'%[ ]%'
        AND LEN(correo) - LEN(REPLACE(correo, N'@', N'')) = 1
        AND CHARINDEX(N'@', correo) > 1
        AND SUBSTRING(correo, CHARINDEX(N'@', correo) + 1, 254) LIKE N'[^.]%.%[^.]'
        AND correo COLLATE Latin1_General_BIN2 NOT LIKE N'%[A-Z]%'));

ALTER TABLE usuario WITH CHECK ADD CONSTRAINT ck_usuario_usuario_forma CHECK (
    LEN(LTRIM(RTRIM(usuario))) > 0
    AND usuario NOT LIKE N'[ ]%'
    AND usuario NOT LIKE N'%[ ]'
    AND usuario COLLATE Latin1_General_BIN2 NOT LIKE N'%[A-Z]%');

-- Nunca se guarda la contraseña: solo un hash BCrypt ($2a$/$2b$/$2y$, dos dígitos de costo, 53 caracteres; 60 en total).
ALTER TABLE usuario WITH CHECK ADD CONSTRAINT ck_usuario_clave_hash_bcrypt CHECK (
    clave_hash COLLATE Latin1_General_BIN2 LIKE N'$2[aby]$[0-9][0-9]$' + REPLICATE(N'[./A-Za-z0-9]', 53));

-- Servicio: la aplicación ya limita la duración a un día (Validaciones.MAX_DURACION_MIN).
ALTER TABLE servicio WITH CHECK ADD CONSTRAINT ck_servicio_duracion_max CHECK (duracion_min <= 1440);

ALTER TABLE enlace_invitacion WITH CHECK ADD CONSTRAINT ck_enlace_invitacion_desactivado CHECK (
    desactivado_en IS NULL OR desactivado_en >= creado_en);
GO

-- ----------------------------------------------------------------------------------------------------
-- V8: citas sin solape  (V8__citas_sin_solape.sql)
-- ----------------------------------------------------------------------------------------------------
-- Citas sin solaparse: para el mismo servicio y día, dos citas no canceladas no pueden cruzarse
-- (a.hora_inicio < b.hora_fin AND b.hora_inicio < a.hora_fin). Que una termine justo cuando empieza la otra sí vale.
-- La aplicación ya lo impide bajo el bloqueo de la fila del servicio; el disparador es la última defensa ante
-- escrituras directas o carreras. uq_cita_servicio_inicio (mismo inicio exacto) se conserva.
--
-- 1) Auditoría previa: si ya hay citas que se solapan, la migración aborta con THROW 50001 y hasta 5 pares de ids.
--    Solo lee: nunca corrige ni borra datos. Flyway ejecuta el script en una sola transacción.
-- 2) Índice filtrado de las citas que ocupan horario, con hora_fin incluida: el disparador lee solo de él (sin ir a
--    la tabla), así que sus bloqueos de rango quedan dentro del servicio y día afectados.
-- 3) Disparador tr_cita_sin_solape (AFTER INSERT, UPDATE).

-- ================================================================== Auditoría previa

DECLARE @pares NVARCHAR(1000) = (SELECT STRING_AGG(CONCAT(N'(', CONVERT(NVARCHAR(36), a_id), N', ',
        CONVERT(NVARCHAR(36), b_id), N')'), N', ')
    FROM (SELECT TOP (5) a.id AS a_id, b.id AS b_id
          FROM cita AS a
          JOIN cita AS b ON b.servicio_id = a.servicio_id AND b.fecha = a.fecha AND a.id < b.id
                        AND a.hora_inicio < b.hora_fin AND b.hora_inicio < a.hora_fin
          WHERE a.estado <> 'Cancelada' AND b.estado <> 'Cancelada'
          ORDER BY a.id, b.id) AS v);
IF @pares IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V8 abortada: hay citas solapadas (citas no canceladas del ' +
        N'mismo servicio y día cuyos horarios se cruzan). Pares de ids de ejemplo (hasta 5): ', @pares,
        N'. No se modificó ningún dato: cancela o mueve una cita de cada par y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- ================================================================== Índice que lee el disparador

-- Mismo filtro que uq_cita_servicio_inicio: el disparador lo nombra en su lectura y la condición debe coincidir.
CREATE INDEX ix_cita_servicio_ocupada ON cita (servicio_id, fecha, hora_inicio) INCLUDE (hora_fin)
    WHERE estado <> 'Cancelada';
GO

-- ================================================================== Disparador

-- Revisa cada fila escrita que ocupa horario contra las demás citas no canceladas de su servicio y día, incluidas
-- las otras filas de la misma sentencia (en un AFTER ya están en la tabla). Una cita no se compara consigo misma.
-- Se saltan las filas cuyo horario no cambió (mismo servicio, día y horas, y ya ocupaban): con la regla vigente no
-- pueden crear un solape, y así atender o editar otros datos no toma bloqueos de rango.
--
-- A prueba de carreras: la lectura usa UPDLOCK + HOLDLOCK (serializable), que bloquea el rango de claves
-- (servicio, día, inicios anteriores al fin de la cita) hasta el final de la transacción. Una transacción que quiera
-- insertar o mover una cita a ese rango espera a que esta termine y luego ve su cita; si las dos ya insertaron, cada
-- lectura espera la fila de la otra y SQL Server elige una víctima (error 1205). Nunca ganan las dos.
-- INDEX + FORCESEEK fijan la búsqueda por servicio y día en el índice filtrado: otro plan (p. ej. por fecha) tomaría
-- bloqueos sobre citas de otros servicios.
-- THROW dentro de un disparador revierte la transacción entera; el mensaje no lleva valores de la base.
CREATE TRIGGER tr_cita_sin_solape ON cita
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS n
        JOIN cita AS o WITH (UPDLOCK, HOLDLOCK, INDEX (ix_cita_servicio_ocupada), FORCESEEK)
            ON o.servicio_id = n.servicio_id AND o.fecha = n.fecha
           AND o.hora_inicio < n.hora_fin AND n.hora_inicio < o.hora_fin
           AND o.id <> n.id
        WHERE n.estado <> 'Cancelada' AND o.estado <> 'Cancelada'
          AND NOT EXISTS (SELECT 1 FROM deleted AS d
                          WHERE d.id = n.id AND d.estado <> 'Cancelada' AND d.servicio_id = n.servicio_id
                            AND d.fecha = n.fecha AND d.hora_inicio = n.hora_inicio AND d.hora_fin = n.hora_fin))
        THROW 50010, N'La cita se solapa con otra del mismo servicio.', 1;
END;
GO

-- ----------------------------------------------------------------------------------------------------
-- V9: ventanas sin cruce  (V9__ventanas_sin_cruce.sql)
-- ----------------------------------------------------------------------------------------------------
-- Ventanas de disponibilidad sin cruzarse: para el mismo servicio y día, dos ventanas no pueden cruzarse
-- (a.hora_inicio < b.hora_fin AND b.hora_inicio < a.hora_fin). Que una termine justo cuando empieza la otra sí vale.
-- La aplicación ya lo valida antes de guardar (Validaciones.errorServicio); el disparador es la última defensa ante
-- escrituras directas.
--
-- 1) Auditoría previa: si ya hay ventanas que se cruzan, la migración aborta con THROW 50001 y hasta 5 pares de ids.
--    Solo lee: nunca corrige ni borra datos. Flyway ejecuta el script en una sola transacción.
-- 2) Disparador tr_ventana_sin_cruce (AFTER INSERT, UPDATE).

-- ================================================================== Auditoría previa

DECLARE @pares NVARCHAR(1500) = (SELECT STRING_AGG(CONCAT(N'(servicio ', CONVERT(NVARCHAR(36), s_id), N': ',
        CONVERT(NVARCHAR(36), a_id), N' y ', CONVERT(NVARCHAR(36), b_id), N')'), N', ')
    FROM (SELECT TOP (5) a.servicio_id AS s_id, a.id AS a_id, b.id AS b_id
          FROM ventana_disponibilidad AS a
          JOIN ventana_disponibilidad AS b ON b.servicio_id = a.servicio_id AND b.dia = a.dia AND a.id < b.id
                                          AND a.hora_inicio < b.hora_fin AND b.hora_inicio < a.hora_fin
          ORDER BY a.servicio_id, a.id, b.id) AS v);
IF @pares IS NOT NULL
BEGIN
    DECLARE @mensaje NVARCHAR(2048) = CONCAT(N'Migración V9 abortada: hay ventanas cruzadas (ventanas de ' +
        N'disponibilidad del mismo servicio y día cuyos horarios se cruzan). Ejemplos de ventanas (hasta 5 pares): ',
        @pares, N'. No se modificó ningún dato: ajusta o borra una ventana de cada par y vuelve a migrar.');
    THROW 50001, @mensaje, 1;
END
GO

-- ================================================================== Disparador

-- Revisa cada fila escrita contra las demás ventanas de su servicio y día, incluidas las otras filas de la misma
-- sentencia (en un AFTER ya están en la tabla). Una ventana no se compara consigo misma: la llave es
-- (servicio_id, id), y el id solo es único dentro de su servicio.
-- La regla se comprueba al final de cada sentencia: quien reemplaza ventanas debe borrar antes de insertar
-- (ServiciosServicio.guardar lo hace) para que ningún paso intermedio las deje cruzadas.
-- THROW dentro de un disparador revierte la transacción entera; el mensaje no lleva valores de la base.
CREATE TRIGGER tr_ventana_sin_cruce ON ventana_disponibilidad
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted AS n
        JOIN ventana_disponibilidad AS o ON o.servicio_id = n.servicio_id AND o.dia = n.dia AND o.id <> n.id
                                        AND o.hora_inicio < n.hora_fin AND n.hora_inicio < o.hora_fin)
        THROW 50020, N'Las ventanas de disponibilidad de un servicio se cruzan.', 1;
END;
GO

-- ----------------------------------------------------------------------------------------------------
-- V10: disparador citas sin bloqueos de mas  (V10__disparador_citas_sin_bloqueos_de_mas.sql)
-- ----------------------------------------------------------------------------------------------------
-- Disparador tr_cita_sin_solape (V8) sin bloqueos de más: la misma regla, el mismo error 50010 y el mismo mensaje,
-- pero las filas cuyo horario no cambia ya no tocan el índice ix_cita_servicio_ocupada.
--
-- En V8 la condición «saltar las filas sin cambio de horario» (anti-semi-join contra deleted) iba en la misma
-- consulta que la lectura con UPDLOCK + HOLDLOCK, y el optimizador la aplicaba DESPUÉS de buscar en el índice: atender
-- una cita (UPDATE estado = 'Atendida') tomaba bloqueos de rango sobre las citas siguientes del servicio y día y podía
-- interbloquearse con una reserva o un traslado. Aquí las filas que de verdad ocupan un horario nuevo se separan antes
-- en una variable de tabla; si no queda ninguna, el disparador termina sin leer cita.
--
-- Solo redefine el disparador: no toca datos ni índices. Flyway ejecuta el script en una sola transacción.

-- Revisa cada fila escrita que ocupa horario contra las demás citas no canceladas de su servicio y día, incluidas
-- las otras filas de la misma sentencia (en un AFTER ya están en la tabla). Una cita no se compara consigo misma.
--
-- 1) Filas a revisar: las de inserted que ocupan horario (estado <> 'Cancelada') y no lo ocupaban ya igual (no hay en
--    deleted la misma cita, no cancelada, con el mismo servicio, día y horas). Con la regla vigente, una fila que no
--    cambió no puede crear un solape; así atender, cancelar o editar otros datos no lee cita ni toma bloqueos de rango.
-- 2) Solo sobre esas filas, la lectura a prueba de carreras: UPDLOCK + HOLDLOCK (serializable) bloquea el rango de
--    claves (servicio, día, inicios anteriores al fin de la cita) hasta el final de la transacción. Una transacción
--    que quiera insertar o mover una cita a ese rango espera a que esta termine y luego ve su cita; si las dos ya
--    insertaron, cada lectura espera la fila de la otra y SQL Server elige una víctima (error 1205). Nunca ganan las
--    dos. INDEX + FORCESEEK fijan la búsqueda por servicio y día en el índice filtrado: otro plan (p. ej. por fecha)
--    tomaría bloqueos sobre citas de otros servicios. Nota (corrige el comentario de V8): el índice no incluye
--    estado, así que el plan va a la tabla (búsqueda por pk_cita) para comprobar o.estado, pero solo con las citas
--    del rango que de verdad se cruzan con la nueva, es decir, cuando ya hay un solape y la sentencia va a fallar.
-- THROW dentro de un disparador revierte la transacción entera; el mensaje no lleva valores de la base.
CREATE OR ALTER TRIGGER dbo.tr_cita_sin_solape ON dbo.cita
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @a_revisar TABLE (
        id          UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        servicio_id UNIQUEIDENTIFIER NOT NULL,
        fecha       DATE             NOT NULL,
        hora_inicio TIME(0)          NOT NULL,
        hora_fin    TIME(0)          NOT NULL);

    INSERT INTO @a_revisar (id, servicio_id, fecha, hora_inicio, hora_fin)
    SELECT n.id, n.servicio_id, n.fecha, n.hora_inicio, n.hora_fin
    FROM inserted AS n
    WHERE n.estado <> 'Cancelada'
      AND NOT EXISTS (SELECT 1 FROM deleted AS d
                      WHERE d.id = n.id AND d.estado <> 'Cancelada' AND d.servicio_id = n.servicio_id
                        AND d.fecha = n.fecha AND d.hora_inicio = n.hora_inicio AND d.hora_fin = n.hora_fin);

    IF @@ROWCOUNT = 0
        RETURN;

    IF EXISTS (
        SELECT 1
        FROM @a_revisar AS n
        JOIN dbo.cita AS o WITH (UPDLOCK, HOLDLOCK, INDEX (ix_cita_servicio_ocupada), FORCESEEK)
            ON o.servicio_id = n.servicio_id AND o.fecha = n.fecha
           AND o.hora_inicio < n.hora_fin AND n.hora_inicio < o.hora_fin
           AND o.id <> n.id
        WHERE o.estado <> 'Cancelada')
        THROW 50010, N'La cita se solapa con otra del mismo servicio.', 1;
END;
GO

-- Registro de las migraciones aplicadas (solo si el esquema quedó completo).
IF OBJECT_ID(N'dbo.tr_cita_sin_solape', N'TR') IS NOT NULL
   AND OBJECT_ID(N'dbo.tr_ventana_sin_cruce', N'TR') IS NOT NULL
   AND OBJECT_ID(N'dbo.ck_cita_cancelada_coherente', N'C') IS NOT NULL
BEGIN
    INSERT INTO historial_migraciones (version, description, checksum, installed_on, success) VALUES
        (1, N'base', 2131899645, SYSUTCDATETIME(), 1),
        (2, N'roles usuarios', -104475106, SYSUTCDATETIME(), 1),
        (3, N'servicios', -978334752, SYSUTCDATETIME(), 1),
        (4, N'clientes citas', -1799514279, SYSUTCDATETIME(), 1),
        (5, N'enlaces', 1136650878, SYSUTCDATETIME(), 1),
        (6, N'indices tablero', 311236963, SYSUTCDATETIME(), 1),
        (7, N'restricciones de datos', -560801525, SYSUTCDATETIME(), 1),
        (8, N'citas sin solape', -1351500152, SYSUTCDATETIME(), 1),
        (9, N'ventanas sin cruce', 25954324, SYSUTCDATETIME(), 1),
        (10, N'disparador citas sin bloqueos de mas', -1442203667, SYSUTCDATETIME(), 1);
    PRINT N'DateManager: esquema creado (V1..V10).';
END
ELSE
    RAISERROR(N'El esquema quedó incompleto: revisa los errores anteriores y no uses esta base.', 16, 1);
GO

SET NOEXEC OFF;
GO
