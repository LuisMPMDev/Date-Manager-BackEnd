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
