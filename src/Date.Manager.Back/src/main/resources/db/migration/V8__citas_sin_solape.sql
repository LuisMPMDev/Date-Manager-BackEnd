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
