package gt.edu.umg.sancarlos.dto;

/** Confirmación simple de una operación sin otro dato que devolver. */
public class MensajeDTO {

    private final String mensaje;

    public MensajeDTO(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getMensaje() {
        return mensaje;
    }
}
