package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Datos de una entrada o salida manual; el origen lo define el Service. */
public class MovimientoInventarioDTO {

    private long idUsuario;
    private BigDecimal cantidad;
    private String observacion;

    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
