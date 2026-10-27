package gt.edu.umg.sancarlos.dto;

/** Datos de entrada para crear una producción mediante PKG_PRODUCCION.SP_CREAR. */
public class CrearProduccionDTO {

    private long idUsuario;
    private String observacion;

    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
