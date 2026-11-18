-- =====================================================================================================
-- DateManager: datos de prueba para la base de datos (equivalentes a la siembra del perfil dev de la API).
--
-- Carga: 2 roles, 2 usuarios, 3 servicios con sus ventanas de disponibilidad, 8 clientes, 5 enlaces de
-- invitación (uno por estado) y 23 citas repartidas alrededor de la fecha en que se ejecuta el script
-- (pasadas atendidas o canceladas, la de hoy y hasta 10 días hacia adelante).
--
-- Requisitos: la base ya existe con el esquema creado (crear-base-de-datos.sql o el arranque de la API).
-- Seguridad: si la base ya tiene cualquier dato (usuarios, roles, servicios, clientes, citas o enlaces) no hace
-- nada; todo se carga en una sola transacción (o se carga todo o no se carga nada). Para otra base distinta de
-- DateManager, cambia la línea marcada con [NOMBRE].
--
-- Cuentas de ejemplo:  marta / demo123 (Administrador)   andres / demo123 (Recepción)
-- !!! demo123 es una clave de demostración deliberadamente falsa. Usa este script SOLO en bases locales
--     de pruebas, nunca en producción. En la base solo se guarda el hash BCrypt.
--
-- Fechas: se calculan con la fecha y hora del servidor SQL (GETDATE). Los instantes de creación y cancelación
-- se guardan en UTC. Los ids son los mismos que genera la siembra de la API, así que los datos coinciden.
-- =====================================================================================================
SET NOCOUNT ON;
GO

USE [DateManager];   -- [NOMBRE]
GO

SET XACT_ABORT ON;
BEGIN TRY
    IF EXISTS (SELECT 1 FROM usuario) OR EXISTS (SELECT 1 FROM rol) OR EXISTS (SELECT 1 FROM servicio)
       OR EXISTS (SELECT 1 FROM cliente) OR EXISTS (SELECT 1 FROM cita) OR EXISTS (SELECT 1 FROM enlace_invitacion)
    BEGIN
        PRINT N'La base ya tiene datos: no se carga nada.';
        RETURN;
    END;

    BEGIN TRANSACTION;

    DECLARE @hoy      DATE         = CAST(GETDATE() AS DATE);
    DECLARE @ahora    DATETIME2(0) = CAST(GETDATE() AS DATETIME2(0));          -- hora local del servidor
    DECLARE @ahoraUtc DATETIME2(3) = CAST(SYSUTCDATETIME() AS DATETIME2(3));   -- instante UTC

    -- ---------------------------------------------------------------- Roles y permisos
    INSERT INTO rol (id, nombre, descripcion) VALUES
        ('0BE8159F-7A56-302E-9CD7-EAEE4FECD385', N'Administrador', N'Acceso completo al sistema'),
        ('31F83373-1B59-364E-BFA3-63369CDCF9FC', N'Recepción', N'Atiende la agenda y a los clientes');

    INSERT INTO rol_permiso (rol_id, permiso)
    SELECT '0BE8159F-7A56-302E-9CD7-EAEE4FECD385', p
    FROM (VALUES ('ver_panel'), ('ver_citas'), ('gestionar_citas'), ('atender_citas'), ('gestionar_servicios'),
                 ('gestionar_clientes'), ('gestionar_usuarios'), ('gestionar_roles'), ('gestionar_enlaces')) AS v(p);

    INSERT INTO rol_permiso (rol_id, permiso)
    SELECT '31F83373-1B59-364E-BFA3-63369CDCF9FC', p
    FROM (VALUES ('ver_citas'), ('gestionar_citas'), ('atender_citas'), ('gestionar_clientes')) AS v(p);

    -- ---------------------------------------------------------------- Usuarios (clave: demo123)
    INSERT INTO usuario (id, nombre, apellido, correo, telefono, usuario, clave_hash, rol_id) VALUES
        ('809207F7-EEC3-3DB2-B401-FB344C940C9D', N'Marta', N'Rivas', N'marta@datemanager.demo', N'300 555 0201', N'marta',
         N'$2a$10$xt7VeSbUVEmwRmkMFigbOOJrH0Ej7JAyc6EaxSS1cuXWWNimRmf2.', '0BE8159F-7A56-302E-9CD7-EAEE4FECD385'),
        ('CDDB278B-7C49-3D09-9C3C-00DAE47DE69E', N'Andrés', N'Pardo', N'andres@datemanager.demo', N'300 555 0202', N'andres',
         N'$2a$10$xt7VeSbUVEmwRmkMFigbOOJrH0Ej7JAyc6EaxSS1cuXWWNimRmf2.', '31F83373-1B59-364E-BFA3-63369CDCF9FC');

    -- ---------------------------------------------------------------- Servicios
    INSERT INTO servicio (id, nombre, duracion_min, activo, valor) VALUES
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', N'Consulta inicial', 30, 1, 40000),
        ('661E2853-EF8F-395E-892A-C326DAF63825', N'Sesión estándar', 60, 1, 80000),
        ('68322BA3-54B1-3B60-AD61-17B593DC981E', N'Sesión extendida', 90, 1, 110000);

    -- Ventanas de disponibilidad (dia ISO: lunes = 1 ... domingo = 7)
    INSERT INTO ventana_disponibilidad (servicio_id, id, dia, hora_inicio, hora_fin) VALUES
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', 'D6549920-43FE-36B8-86CB-8D835C2CB6D1', 1, '09:00:00', '13:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '137A68FC-8124-399F-98B7-22418DBD8CB2', 1, '15:00:00', '18:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '30BC8BE2-9180-314F-BACD-073AB175095C', 2, '09:00:00', '13:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '731F3D17-E220-386D-B3AE-EA7C8C143223', 2, '15:00:00', '18:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', 'CBD37813-96DB-3DA5-9977-D87326295A2A', 3, '09:00:00', '13:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '00C74BE1-1116-3A6D-B4AE-BA486E74D011', 3, '15:00:00', '18:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '780D32F7-EB7D-3DF9-969C-7E372BE4ECD2', 4, '09:00:00', '13:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '2A23A088-C482-3DE7-8D3C-C7DA682AD725', 4, '15:00:00', '18:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '56A6C09B-8FEB-31E8-AA2D-908F3067C7E9', 5, '09:00:00', '13:00:00'),
        ('18974E39-7D93-3D10-BAA9-8797BAD9D80D', '536CFB72-66DD-36BF-BDB8-E8CB3919C4EE', 5, '15:00:00', '18:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', 'E9D386FA-6088-39AD-9E15-19C9A9D2C45D', 1, '08:00:00', '12:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', 'E869DAC1-8E0D-3711-B7CA-7C745708EBAD', 1, '14:00:00', '17:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', 'C3E58DAB-BC31-3968-B4D7-D0D3AEF1A394', 2, '08:00:00', '12:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', 'D15D59F9-2A65-3CA9-A354-4EBAF3166ACD', 2, '14:00:00', '17:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', '81D072D4-501E-31B8-ABC2-FA2C5E17CCB1', 3, '08:00:00', '12:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', '73551C02-A477-3FED-B20B-CA6E979A8247', 3, '14:00:00', '17:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', 'F4419427-274A-38CF-BDC2-4452F8847466', 4, '08:00:00', '12:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', '7EF571AE-C88C-36C6-BB82-84A500E92BA1', 4, '14:00:00', '17:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', '2606A43C-0265-3A49-8DCF-E8BCFFE6D559', 5, '08:00:00', '12:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', '3D3AF54C-14EC-3909-9543-8AD6515F3266', 5, '14:00:00', '17:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', '4D50A622-F3E2-3232-84E6-E18A6BDBE1F3', 6, '08:00:00', '12:00:00'),
        ('661E2853-EF8F-395E-892A-C326DAF63825', 'CBC6512C-8463-3510-9F08-7D11E5A789CF', 6, '14:00:00', '17:00:00'),
        ('68322BA3-54B1-3B60-AD61-17B593DC981E', '6B2A463F-0BE5-381E-A2EE-5EA2DB5357C7', 2, '09:00:00', '13:00:00'),
        ('68322BA3-54B1-3B60-AD61-17B593DC981E', 'FE243028-8FF8-3B71-BF01-5D97E80583A0', 4, '09:00:00', '13:00:00'),
        ('68322BA3-54B1-3B60-AD61-17B593DC981E', 'BC6092AC-E7F6-3222-BFAA-426BBC94EECB', 5, '14:00:00', '18:00:00');

    -- ---------------------------------------------------------------- Clientes
    INSERT INTO cliente (id, nombre, apellido, telefono, correo) VALUES
        ('A7B7D8C6-FBE9-3044-9A11-5FE2BB687FC8', N'Camila', N'Rojas', N'300 555 0101', N'camila@correo.com'),
        ('2AF99442-D836-3940-85F2-BB5BBEF286E2', N'Daniel', N'Ortiz', N'300 555 0102', N'daniel@correo.com'),
        ('DD963CC0-AE3A-357D-85A9-FDFE554CD005', N'Valentina', N'Mejía', N'300 555 0103', N'valentina@correo.com'),
        ('8673F6C3-8C70-3940-B58C-FBD65B0246AC', N'Sebastián', N'Cruz', N'300 555 0104', N'sebastian@correo.com'),
        ('07500101-5071-3161-AB89-F4451418C06F', N'Isabela', N'Torres', N'300 555 0105', N'isabela@correo.com'),
        ('E3943A76-E924-3ACF-9954-C414C91DA669', N'Mateo', N'Herrera', N'300 555 0106', N'mateo@correo.com'),
        ('B655AE8C-D676-3D0F-A56D-A5EE7A8E7206', N'Lucía', N'Vargas', N'300 555 0107', N'lucia@correo.com'),
        ('9C3B7267-961E-3562-810E-C67B4374D56E', N'Julián', N'Peña', N'300 555 0108', N'julian@correo.com');

    -- ---------------------------------------------------------------- Enlaces de invitación (uno por estado)
    -- codigo: 22 caracteres Base64 URL, distingue mayúsculas. Los de ejemplo son fáciles de recordar.
    INSERT INTO enlace_invitacion (id, codigo, creado_por, creado_en, vence_en, max_usos, usos, desactivado_en) VALUES
        -- Activo: sin límite de usos
        ('62114351-878C-3976-8C38-45CDF09D98BF', N'DemoEnlaceActivo000001', '809207F7-EEC3-3DB2-B401-FB344C940C9D',
         DATEADD(DAY, -2, @ahoraUtc), DATEADD(DAY, 28, @ahoraUtc), NULL, 0, NULL),
        -- Activo: máximo 5 usos, 1 gastado
        ('10011B06-922D-394A-87A2-52BC8778C91F', N'DemoEnlaceLimitado0002', '809207F7-EEC3-3DB2-B401-FB344C940C9D',
         DATEADD(DAY, -5, @ahoraUtc), DATEADD(DAY, 25, @ahoraUtc), 5, 1, NULL),
        -- Agotado: 1 uso de 1
        ('9489F1B5-D42D-37C8-95B1-A6955FF30F65', N'DemoEnlaceAgotado00003', '809207F7-EEC3-3DB2-B401-FB344C940C9D',
         DATEADD(DAY, -4, @ahoraUtc), DATEADD(DAY, 4, @ahoraUtc), 1, 1, NULL),
        -- Vencido
        ('5F7C8512-CD91-3F03-B95F-89846A34C442', N'DemoEnlaceVencido00004', '809207F7-EEC3-3DB2-B401-FB344C940C9D',
         DATEADD(DAY, -10, @ahoraUtc), DATEADD(DAY, -3, @ahoraUtc), NULL, 0, NULL),
        -- Desactivado a mano
        ('7ED6E4C0-8857-3D29-BCC9-7C51DD3E4F0E', N'DemoEnlaceDesact00005', '809207F7-EEC3-3DB2-B401-FB344C940C9D',
         DATEADD(DAY, -5, @ahoraUtc), DATEADD(DAY, 10, @ahoraUtc), 3, 0, DATEADD(DAY, -1, @ahoraUtc));

    -- ---------------------------------------------------------------- Citas
    -- Plan: dia = desplazamiento en días respecto a hoy; srv = 0 Consulta, 1 Estándar, 2 Extendida;
    -- hueco = n-ésimo horario del día; cli = 0..7 (en el orden de los clientes de arriba).
    -- Cada cita cae en el día hábil más cercano (hacia el pasado si dia < 0, hacia adelante si no) en que el
    -- servicio atiende y en un horario libre, así que nunca se solapan y los datos siguen vigentes sea cuando sea
    -- que se ejecute el script. Una cita «Atendida» que quedaría en el futuro pasa a «Confirmada».
    DECLARE @servicio TABLE (idx INT PRIMARY KEY, id UNIQUEIDENTIFIER, duracion INT);
    INSERT INTO @servicio VALUES (0, '18974E39-7D93-3D10-BAA9-8797BAD9D80D', 30), (1, '661E2853-EF8F-395E-892A-C326DAF63825', 60), (2, '68322BA3-54B1-3B60-AD61-17B593DC981E', 90);

    DECLARE @cliente TABLE (idx INT PRIMARY KEY, id UNIQUEIDENTIFIER);
    INSERT INTO @cliente VALUES
        (0, 'A7B7D8C6-FBE9-3044-9A11-5FE2BB687FC8'),
        (1, '2AF99442-D836-3940-85F2-BB5BBEF286E2'),
        (2, 'DD963CC0-AE3A-357D-85A9-FDFE554CD005'),
        (3, '8673F6C3-8C70-3940-B58C-FBD65B0246AC'),
        (4, '07500101-5071-3161-AB89-F4451418C06F'),
        (5, 'E3943A76-E924-3ACF-9954-C414C91DA669'),
        (6, 'B655AE8C-D676-3D0F-A56D-A5EE7A8E7206'),
        (7, '9C3B7267-961E-3562-810E-C67B4374D56E');

    DECLARE @plan TABLE (
        n INT PRIMARY KEY, cita_id UNIQUEIDENTIFIER, dia INT, srv INT, hueco INT, cli INT, estado NVARCHAR(20),
        creada_h INT NULL, cancel_h INT NULL, enlace_id UNIQUEIDENTIFIER NULL);
    INSERT INTO @plan (n, cita_id, dia, srv, hueco, cli, estado, creada_h, cancel_h, enlace_id) VALUES
        (1, 'A3B31C02-BA85-3CD8-B686-66736421CF69', -19, 1, 0, 0, N'Atendida', NULL, NULL, NULL),
        (2, '98BC481B-51AB-360B-B44B-84FA24FB99DE', -18, 0, 2, 1, N'Atendida', NULL, NULL, NULL),
        (3, 'CA45459A-0E8F-3E6A-ADC4-6F4569C7D6E6', -15, 2, 0, 2, N'Atendida', NULL, NULL, NULL),
        (4, '794F22A4-18C2-3089-BBD6-65679F990FD6', -13, 1, 1, 3, N'Atendida', NULL, NULL, NULL),
        (5, '5EF44AB8-5A36-310C-8DBE-0361A2837EC0', -10, 0, 0, 4, N'Atendida', NULL, NULL, NULL),
        (6, '8F800FB4-724B-38EF-8B2F-43A73177B24C', -8, 1, 2, 5, N'Atendida', NULL, NULL, NULL),
        (7, 'ADF5F1C7-131D-38E0-96C9-513DEC75902D', -6, 0, 1, 6, N'Cancelada', NULL, NULL, NULL),
        (8, '639AC0A2-C4D2-3D71-9B55-82A3DF2EEACD', -4, 2, 1, 7, N'Atendida', NULL, NULL, NULL),
        (9, 'CE7B610F-4C0D-37A1-B8CC-F13C3205E4AC', -3, 0, 3, 0, N'Atendida', NULL, NULL, NULL),
        (10, '15F27CBC-7C4D-3335-BFC7-3B0703CBAE93', -2, 1, 0, 1, N'Atendida', NULL, NULL, NULL),
        (11, 'D8E5D8DF-0B92-3BA2-B672-0464EFA6CA62', -1, 0, 1, 2, N'Atendida', NULL, NULL, NULL),
        (12, '08DE254C-377B-3FFE-BBD9-6475E7D141A3', 0, 1, 2, 4, N'Atendida', NULL, NULL, NULL),
        (13, '21FA1AF3-32E8-3347-8D55-D73A1B762EBC', 0, 0, 1, 3, N'Confirmada', NULL, NULL, NULL),
        (14, '027414D7-0C90-3E1F-B429-52F6FE999D7D', 1, 0, 0, 5, N'Confirmada', NULL, NULL, '10011B06-922D-394A-87A2-52BC8778C91F'),
        (15, '8C81C087-6E93-3590-9C97-54B86C8025C4', 1, 1, 4, 6, N'Cancelada', 72, 5, NULL),
        (16, '27C0AED3-6D36-3141-90EF-2467277D2061', 2, 2, 0, 7, N'Confirmada', 2, NULL, '9489F1B5-D42D-37C8-95B1-A6955FF30F65'),
        (17, '54BFA40A-CE3F-3E0B-84BF-278073743F03', 3, 0, 2, 0, N'Confirmada', 20, NULL, NULL),
        (18, '99F22910-4E43-3AE7-8475-EBF6606D7D7A', 4, 1, 1, 1, N'Confirmada', NULL, NULL, NULL),
        (19, '0588898C-89A9-3FDB-A9CA-51F1DCC10FA9', 5, 0, 5, 3, N'Confirmada', NULL, NULL, NULL),
        (20, '384FBF91-FDBC-3763-8CF0-EEB1A7E66C9A', 6, 2, 1, 2, N'Confirmada', NULL, NULL, NULL),
        (21, 'CC1554FA-84A8-317A-BF4E-012C753BB909', 8, 1, 0, 4, N'Confirmada', NULL, NULL, NULL),
        (22, '5BA4EA60-0965-3BE4-B2B5-2EF1984C99FA', 9, 0, 1, 5, N'Confirmada', NULL, NULL, NULL),
        (23, '8211B5CF-7083-3289-8977-5DB6E305CB63', 10, 1, 2, 6, N'Confirmada', NULL, NULL, NULL);

    DECLARE @n INT, @citaId UNIQUEIDENTIFIER, @dia INT, @srv INT, @hueco INT, @cli INT, @estado NVARCHAR(20),
            @creadaH INT, @cancelH INT, @enlaceId UNIQUEIDENTIFIER;
    DECLARE @paso INT, @d INT, @i INT, @k INT, @f DATE, @dow INT, @fecha DATE, @servicioId UNIQUEIDENTIFIER,
            @duracion INT, @base INT, @salto INT, @slots INT, @slot INT, @inicio TIME(0), @fin TIME(0),
            @clienteId UNIQUEIDENTIFIER, @creada DATETIME2(3), @cancelada DATETIME2(3), @elegido BIT;

    DECLARE citas_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT n, cita_id, dia, srv, hueco, cli, estado, creada_h, cancel_h, enlace_id FROM @plan ORDER BY n;
    OPEN citas_cursor;
    FETCH NEXT FROM citas_cursor INTO @n, @citaId, @dia, @srv, @hueco, @cli, @estado, @creadaH, @cancelH, @enlaceId;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        SELECT @servicioId = id, @duracion = duracion FROM @servicio WHERE idx = @srv;
        SELECT @clienteId = id FROM @cliente WHERE idx = @cli;

        -- Día hábil más cercano en que el servicio atiende (dow: 0 = lunes ... 6 = domingo)
        SET @paso = CASE WHEN @dia < 0 THEN -1 ELSE 1 END;
        SET @d = @dia; SET @i = 0; SET @fecha = NULL;
        WHILE @i < 8 AND @fecha IS NULL
        BEGIN
            SET @f = DATEADD(DAY, @d, @hoy);
            SET @dow = DATEDIFF(DAY, '19000101', @f) % 7;
            IF (@srv = 0 AND @dow <= 4) OR (@srv = 1 AND @dow <= 5) OR (@srv = 2 AND @dow IN (1, 3, 4))
                SET @fecha = @f;
            ELSE
                SET @d = @d + @paso;
            SET @i = @i + 1;
        END;
        IF @fecha IS NULL THROW 50100, N'No se encontró un día con horario para el servicio.', 1;

        -- Horarios del día: minuto del primero, tamaño del salto y cantidad
        IF @srv = 0
            SELECT @base = 540, @salto = 30, @slots = 6;                                       -- 09:00 .. 11:30
        ELSE IF @srv = 1
            SELECT @base = 480, @salto = 60, @slots = 4;                                       -- 08:00 .. 11:00
        ELSE
            SELECT @base = CASE WHEN @dow = 4 THEN 840 ELSE 540 END, @salto = 90, @slots = 2;  -- vie 14:00, mar/jue 09:00

        -- n-ésimo horario; si ya está tomado se prueba el siguiente
        SET @k = 0; SET @elegido = 0;
        WHILE @k < @slots AND @elegido = 0
        BEGIN
            SET @slot = (@hueco + @k) % @slots;
            SET @inicio = DATEADD(MINUTE, @base + @slot * @salto, CAST('00:00' AS TIME(0)));
            IF NOT EXISTS (SELECT 1 FROM cita WHERE servicio_id = @servicioId AND fecha = @fecha AND hora_inicio = @inicio)
                SET @elegido = 1;
            SET @k = @k + 1;
        END;
        IF @elegido = 0 THROW 50101, N'No quedan horarios libres para la cita de ejemplo.', 1;
        SET @fin = DATEADD(MINUTE, @duracion, @inicio);

        -- Una cita «Atendida» no puede estar en el futuro
        IF @estado = N'Atendida'
           AND DATEADD(SECOND, DATEDIFF(SECOND, CAST('00:00' AS TIME(0)), @inicio), CAST(@fecha AS DATETIME2(0))) > @ahora
            SET @estado = N'Confirmada';

        -- Instantes UTC de creación y cancelación
        SET @creada = CASE
            WHEN @creadaH IS NOT NULL THEN DATEADD(HOUR, -@creadaH, @ahoraUtc)
            WHEN DATEADD(DAY, -3, CAST(@fecha AS DATETIME2(3))) < DATEADD(DAY, -2, @ahoraUtc)
                THEN DATEADD(DAY, -3, CAST(@fecha AS DATETIME2(3)))
            ELSE DATEADD(DAY, -2, @ahoraUtc) END;
        SET @cancelada = CASE
            WHEN @estado <> N'Cancelada' THEN NULL
            WHEN @cancelH IS NOT NULL THEN DATEADD(HOUR, -@cancelH, @ahoraUtc)
            ELSE DATEADD(HOUR, 1, @creada) END;

        INSERT INTO cita (id, servicio_id, cliente_id, fecha, hora_inicio, hora_fin, estado, creada_en, cancelada_en, enlace_id)
        VALUES (@citaId, @servicioId, @clienteId, @fecha, @inicio, @fin, @estado, @creada, @cancelada, @enlaceId);

        FETCH NEXT FROM citas_cursor INTO @n, @citaId, @dia, @srv, @hueco, @cli, @estado, @creadaH, @cancelH, @enlaceId;
    END;
    CLOSE citas_cursor;
    DEALLOCATE citas_cursor;

    COMMIT TRANSACTION;

    PRINT N'DateManager: datos de prueba cargados (2 roles, 2 usuarios, 3 servicios, 8 clientes, 5 enlaces, 23 citas).';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
