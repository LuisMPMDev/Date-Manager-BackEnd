package com.datemanager;

/** Punto de entrada provisional: solo imprime la versión. El arranque real llega en una tarea posterior. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String version = Main.class.getPackage().getImplementationVersion();
        System.out.println("Date Manager API " + (version == null ? "1.0-SNAPSHOT" : version));
    }
}
