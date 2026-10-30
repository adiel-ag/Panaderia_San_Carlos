package gt.edu.umg.sancarlos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Datos de una entrada o salida manual; el origen lo define el Service y el
 * usuario es el de la sesión (un idUsuario en el JSON se ignora).
 */
@JsonIgnoreProperties({"idUsuario"})
public class MovimientoInventarioDTO {

    private BigDecimal cantidad;
    private String observacion;

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
