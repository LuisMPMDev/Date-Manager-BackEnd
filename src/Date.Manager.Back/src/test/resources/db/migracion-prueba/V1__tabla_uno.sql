CREATE TABLE prueba_uno (id INT NOT NULL PRIMARY KEY, texto NVARCHAR(50) NOT NULL)
go
INSERT INTO prueba_uno VALUES (1, N'año ñandú 😀')
  Go  
-- CREATE VIEW debe ser la primera sentencia de su lote: sin separar por GO no compilaría.
CREATE VIEW vista_uno AS SELECT id FROM prueba_uno
GO
