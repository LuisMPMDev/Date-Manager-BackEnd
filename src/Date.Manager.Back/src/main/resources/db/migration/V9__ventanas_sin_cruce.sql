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
