package gt.edu.umg.sancarlos.dto;

/** Respuesta de consulta de la receta activa asociada a un producto. */
public class RecetaActivaDTO {

    private final long idProducto;
    private final long idReceta;

    public RecetaActivaDTO(long idProducto, long idReceta) {
        this.idProducto = idProducto;
        this.idReceta = idReceta;
    }

    public long getIdProducto() {
        return idProducto;
    }

    public long getIdReceta() {
        return idReceta;
    }
}
