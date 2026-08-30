package App.Domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class Appointment {


    public Scanner sc = new Scanner(System.in);

    public Appointment(UUID id, UUID service, UUID client, LocalTime start, LocalTime end, boolean status, LocalDate date) {
        this.id = id;
        Service = service;
        Client = client;
        Start = start;
        End = end;
        Status = status;
        Date = date;
    }

    private UUID id;
    private UUID Service;
    private UUID Client;
    private LocalTime Start;
    private LocalTime End;
    private boolean Status;
    private LocalDate Date;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getService() {
        return Service;
    }

    public void setService(UUID service) {
        Service = service;
    }

    public UUID getClient() {
        return Client;
    }

    public void setClient(UUID client) {
        Client = client;
    }

    public LocalTime getStart() {
        return Start;
    }

    public void setStart(LocalTime start) {
        Start = start;
    }

    public LocalTime getEnd() {
        return End;
    }

    public void setEnd(LocalTime end) {
        End = end;
    }

    public boolean getStatus() {
        return Status;
    }

    public void setStatus(boolean status) {
        Status = status;
    }

    public LocalDate getDate() {
        return Date;
    }

    public void setDate(LocalDate date) {
        Date = date;
    }

    public void Create() {
        System.out.println("Ingrese el ID del Appointment:");
        this.id = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese el ID del Service:");
        this.Service = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese el ID del Client:");
        this.Client = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese la hora de inicio (HH:MM):");
        this.Start = LocalTime.parse(sc.nextLine());

        System.out.println("Ingrese la hora de fin (HH:MM):");
        this.End = LocalTime.parse(sc.nextLine());

        System.out.println("Ingrese el estado (true/false):");
        this.Status = Boolean.parseBoolean(sc.nextLine());

        System.out.println("Ingrese la fecha (YYYY-MM-DD):");
        this.Date = LocalDate.parse(sc.nextLine());
    }

    public List<Appointment> selectByDate(LocalDate date) {
        List<Appointment> result = new ArrayList<>();
        if (this.Date.equals(date)) {
            result.add(this);
        }
        return result;
    }

    public Appointment selectById(UUID id) {
        if (this.id.equals(id)) {
            return this;
        }
        return null;
    }

    public List<Appointment> selectByClientId(UUID clientId) {
        List<Appointment> result = new ArrayList<>();
        if (this.Client.equals(clientId)) {
            result.add(this);
        }
        return result;
    }

    public List<Appointment> selectByIdAndDate(UUID clientId, LocalDate date) {
        List<Appointment> result = new ArrayList<>();
        if (this.Client.equals(clientId) && this.Date.equals(date)) {
            result.add(this);
        }
        return result;
    }

    public Appointment update(Appointment updated) {
        this.Service = updated.getService();
        this.Client = updated.getClient();
        this.Start = updated.getStart();
        this.End = updated.getEnd();
        this.Status = updated.getStatus();
        this.Date = updated.getDate();
        return this;
    }

    public void delete(UUID id) {
        System.out.println("Appointment Eliminado");
    }
}
