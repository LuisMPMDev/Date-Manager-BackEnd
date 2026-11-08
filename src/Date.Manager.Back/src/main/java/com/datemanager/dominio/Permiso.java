package com.datemanager.dominio;

/** Catálogo de permisos; {@link #id()} es el identificador snake_case que viaja en el JSON. */
public enum Permiso {
    VER_PANEL("ver_panel", "Ver el panel"),
    VER_CITAS("ver_citas", "Ver citas"),
    GESTIONAR_CITAS("gestionar_citas", "Cancelar y mover citas"),
    ATENDER_CITAS("atender_citas", "Marcar citas como atendidas"),
    GESTIONAR_SERVICIOS("gestionar_servicios", "Gestionar servicios y horarios"),
    GESTIONAR_CLIENTES("gestionar_clientes", "Gestionar clientes"),
    GESTIONAR_USUARIOS("gestionar_usuarios", "Gestionar usuarios"),
    GESTIONAR_ROLES("gestionar_roles", "Gestionar roles y permisos"),
    GESTIONAR_ENLACES("gestionar_enlaces", "Gestionar enlaces de invitación");

    private final String id;
    private final String etiqueta;

    Permiso(String id, String etiqueta) {
        this.id = id;
        this.etiqueta = etiqueta;
    }

    public String id() {
        return id;
    }

    /** Texto en español para mostrar el permiso en el panel. */
    public String etiqueta() {
        return etiqueta;
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
