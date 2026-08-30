package App.Domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class Rol {


    public Scanner sc = new Scanner(System.in);


    public Rol(UUID id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }


    private UUID id;
    private String name;
    private String description;



    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public void Create() {
        System.out.println("Ingrese el ID del Rol:");
        this.id = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese el nombre del Rol:");
        this.name = sc.nextLine();

        System.out.println("Ingrese la descripción del Rol:");
        this.description = sc.nextLine();
    }

    public Rol selectById(UUID id) {
        if (this.id.equals(id)) {
            return this;
        }
        return null;
    }

    public List<Rol> selectAll(){
        return new ArrayList<>();
    }

    public void delete(UUID id) {
        System.out.println("Rol Eliminado");
    }

}
