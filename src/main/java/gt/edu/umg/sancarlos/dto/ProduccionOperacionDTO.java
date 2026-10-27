package gt.edu.umg.sancarlos.dto;

/** Confirmación de una operación aplicada sobre una producción. */
public class ProduccionOperacionDTO {

    private final long idProduccion;
    private final String mensaje;

    public ProduccionOperacionDTO(long idProduccion, String mensaje) {
        this.idProduccion = idProduccion;
        this.mensaje = mensaje;
    }

    public long getIdProduccion() {
        return idProduccion;
    }

    public String getMensaje() {
        return mensaje;
    }
}
