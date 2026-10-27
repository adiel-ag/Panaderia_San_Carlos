package gt.edu.umg.sancarlos.dto;

/** Datos para despachar un pedido; el ID del pedido viaja en la ruta. */
public class DespacharPedidoDTO {

    private long idUsuario;
    private String observacion;

    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
