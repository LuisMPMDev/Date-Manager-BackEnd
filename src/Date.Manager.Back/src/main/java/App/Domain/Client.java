package App.Domain;

import java.util.Scanner;
import java.util.UUID;

public class Client {


    public Scanner sc = new Scanner(System.in);


    public Client(UUID id, String name, String lastName, String phone, String email) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.phone = phone;
        this.email = email;
    }


    private UUID id;
    private String name;
    private String lastName;
    private String phone;
    private String email;


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

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public void Create() {
        System.out.println("Ingrese el ID del Cliente:");
        this.id = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese el nombre:");
        this.name = sc.nextLine();

        System.out.println("Ingrese el apellido:");
        this.lastName = sc.nextLine();

        System.out.println("Ingrese el teléfono:");
        this.phone = sc.nextLine();

        System.out.println("Ingrese el email:");
        this.email = sc.nextLine();
    }

    public Client selectById(UUID id) {
        if (this.id.equals(id)) {
            return this;
        }
        return null;
    }

    public Client update(Client updated) {
        this.name = updated.getName();
        this.lastName = updated.getLastName();
        this.phone = updated.getPhone();
        this.email = updated.getEmail();
        return this;
    }

    public void delete(UUID id) {
        System.out.println("Cliente Eliminado");
    }
}

