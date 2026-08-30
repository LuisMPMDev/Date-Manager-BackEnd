package App.Domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class Service {


    public Scanner sc = new Scanner(System.in);


    public Service(UUID id, String name, int duration, boolean status, double valor, List<AvailabilityWindow> availabilityWindow) {
        this.id = id;
        this.name = name;
        this.duration = duration;
        this.status = status;
        this.valor = valor;
        AvailabilityWindow = availabilityWindow;
    }

    private UUID id;
    private String name;
    private int duration;
    private boolean status;
    private double valor;
    private List<AvailabilityWindow> AvailabilityWindow;

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

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }

    public List<AvailabilityWindow> getAvailabilityWindow() {
        return AvailabilityWindow;
    }

    public void setAvailabilityWindow(List<AvailabilityWindow> availabilityWindow) {
        AvailabilityWindow = availabilityWindow;
    }


    public void Create() {
        System.out.println("Ingrese el ID del Service:");
        this.id = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese el nombre del Service:");
        this.name = sc.nextLine();

        System.out.println("Ingrese la duración en minutos:");
        this.duration = Integer.parseInt(sc.nextLine());

        System.out.println("Ingrese el estado (true/false):");
        this.status = Boolean.parseBoolean(sc.nextLine());

        System.out.println("Ingrese el valor:");
        this.valor = Double.parseDouble(sc.nextLine());
    }

    public Service selectById(UUID id) {
        if (this.id.equals(id)) {
            return this;
        }
        return null;
    }

    public List<Service> selectByStatus(Boolean status) {
        List<Service> result = new ArrayList<>();
        if (this.status == status) {
            result.add(this);
        }
        return result;
    }

    public void assignAvailabilityWindow(UUID availabilityWindowId) {
        // En una implementación real, aquí se buscaría y agregaría el AvailabilityWindow
        // a la lista. Simulamos la asignación creando un objeto temporal.
        AvailabilityWindow aw = new AvailabilityWindow();
        aw.setId(availabilityWindowId);
        this.AvailabilityWindow.add(aw);
        System.out.println("Disponibilidad asignada al Servicio");
    }

    public Service update(Service updated) {
        this.name = updated.getName();
        this.duration = updated.getDuration();
        this.status = updated.getStatus();
        this.valor = updated.getValor();
        return this;
    }

    public List<AvailabilityWindow> getAvailabilityWindow(UUID id) {
        List<AvailabilityWindow> result = new ArrayList<>();
        for (AvailabilityWindow aw : this.AvailabilityWindow) {
            if (aw.getId().equals(id)) {
                result.add(aw);
            }
        }
        return result;
    }

    public void delete(UUID id) {
        System.out.println("Service Eliminado");
    }


}
