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
