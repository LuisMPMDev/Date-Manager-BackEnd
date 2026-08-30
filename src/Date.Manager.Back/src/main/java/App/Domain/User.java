package App.Domain;

import java.util.Scanner;
import java.util.UUID;

public class User {


    public Scanner sc = new Scanner(System.in);

    public User(UUID id, String name, String lastName, String email, String phone, String user, String password, UUID rol_id) {

        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.user = user;
        this.password = password;
        this.rol_id = rol_id;
    }

    public User(){

    }

    private UUID id;
    private String name;
    private String lastName;
    private String email;
    private String phone;
    private String user;
    private String password;
    private UUID rol_id;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UUID getRol_id() {
        return rol_id;
    }

    public void setRol_id(UUID rol_id) {
        this.rol_id = rol_id;
    }


    public void Create(){
        System.out.println("Ingrese el ID del user");
        this.id = UUID.fromString(sc.nextLine());
        //this.id = UUID.randomUUID();

        System.out.println("Ingrese el nombre:");
        this.name = sc.nextLine();

        System.out.println("Ingrese el apellido:");
        this.lastName = sc.nextLine();

        System.out.println("Ingrese el correo:");
        this.email = sc.nextLine();

        System.out.println("Ingrese el teléfono:");
        this.phone = sc.nextLine();

        System.out.println("Ingrese el usuario:");
        this.user = sc.nextLine();

        System.out.println("Ingrese la contraseña:");
        this.password = sc.nextLine();

    }

    public boolean Login(String user, String password){

        if(user == this.user && password == this.password){
            return true;
        }
        return false;
    }

    public void rolAssignament(UUID id, UUID rol_id){

        if(this.id == id){
            this.setRol_id(rol_id);
        }
    }

    public User update(User Updated){

        this.name = Updated.getName();
        return this;
    }

    public void delete(UUID id){
        System.out.println("Usuario Eliminado");
    }
}
