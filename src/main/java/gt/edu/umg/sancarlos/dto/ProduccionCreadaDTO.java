package gt.edu.umg.sancarlos.dto;

/** Respuesta de creación de una producción. */
public class ProduccionCreadaDTO {

    private final long idProduccion;

    public ProduccionCreadaDTO(long idProduccion) {
        this.idProduccion = idProduccion;
    }

    public long getIdProduccion() {
        return idProduccion;
    }
}
