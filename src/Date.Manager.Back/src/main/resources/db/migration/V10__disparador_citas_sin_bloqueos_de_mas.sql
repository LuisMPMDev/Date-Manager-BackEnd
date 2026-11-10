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
