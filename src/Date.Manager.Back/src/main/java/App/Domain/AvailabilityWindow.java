package App.Domain;



import java.time.LocalTime;
import java.util.Scanner;
import java.util.UUID;

public class AvailabilityWindow {


    public Scanner sc = new Scanner(System.in);



    public AvailabilityWindow(UUID id, String date, LocalTime startHour, LocalTime endHour) {
        this.id = id;
        this.date = date;
        this.startHour = startHour;
        this.endHour = endHour;
    }

    private UUID id;
    private String date;
    private LocalTime startHour;
    private LocalTime endHour;

    public AvailabilityWindow() {

    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public LocalTime getStartHour() {
        return startHour;
    }

    public void setStartHour(LocalTime startHour) {
        this.startHour = startHour;
    }

    public LocalTime getEndHour() {
        return endHour;
    }

    public void setEndHour(LocalTime endHour) {
        this.endHour = endHour;
    }

    public void Create() {
        System.out.println("Ingrese el ID del AvailabilityWindow:");
        this.id = UUID.fromString(sc.nextLine());

        System.out.println("Ingrese el día (LUNES, MARTES, etc.):");
        this.date = sc.nextLine();

        System.out.println("Ingrese la hora de inicio (HH:MM):");
        this.startHour = LocalTime.parse(sc.nextLine());

        System.out.println("Ingrese la hora de fin (HH:MM):");
        this.endHour = LocalTime.parse(sc.nextLine());
    }

    public void delete(UUID id) {
        System.out.println("AvailabilityWindow Eliminado");
    }

}
