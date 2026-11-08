package com.datemanager.dominio;

/** Catálogo de permisos; {@link #id()} es el identificador snake_case que viaja en el JSON. */
public enum Permiso {
    VER_PANEL("ver_panel"),
    VER_CITAS("ver_citas"),
    GESTIONAR_CITAS("gestionar_citas"),
    ATENDER_CITAS("atender_citas"),
    GESTIONAR_SERVICIOS("gestionar_servicios"),
    GESTIONAR_CLIENTES("gestionar_clientes"),
    GESTIONAR_USUARIOS("gestionar_usuarios"),
    GESTIONAR_ROLES("gestionar_roles"),
    GESTIONAR_ENLACES("gestionar_enlaces");

    private final String id;

    Permiso(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Permiso deId(String id) {
        for (Permiso permiso : values()) {
            if (permiso.id.equals(id)) {
                return permiso;
            }
        }
        throw new ErrorNegocio(CodigoError.VALIDACION, "Permiso desconocido: " + id + ".");
    }
}
