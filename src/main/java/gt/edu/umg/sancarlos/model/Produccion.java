package gt.edu.umg.sancarlos.model;

/**
 * Representa una producción registrada por un usuario.
 */
public class Produccion {

    private long idProduccion;
    private long idUsuario;
    private String observacion;

    public Produccion() {
    }

    public Produccion(long idProduccion, long idUsuario, String observacion) {
        this.idProduccion = idProduccion;
        this.idUsuario = idUsuario;
        this.observacion = observacion;
    }

    public long getIdProduccion() {
        return idProduccion;
    }

    public void setIdProduccion(long idProduccion) {
        this.idProduccion = idProduccion;
    }

    public long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
