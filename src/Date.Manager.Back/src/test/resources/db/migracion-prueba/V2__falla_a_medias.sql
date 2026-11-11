CREATE TABLE prueba_dos (id INT NOT NULL)
GO
INSERT INTO prueba_uno VALUES (2, N'dos')
GO
THROW 50001, N'Falla a propósito', 1;
GO
