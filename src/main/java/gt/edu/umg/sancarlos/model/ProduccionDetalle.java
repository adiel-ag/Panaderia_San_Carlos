package gt.edu.umg.sancarlos.model;

import java.math.BigDecimal;

/** Representa una línea del plan calculado por Oracle para una producción. */
public class ProduccionDetalle {

    private long idProduccionDetalle;
    private long idProduccion;
    private long idProducto;
    private long idReceta;
    private BigDecimal cantidadPlanificada;
    private BigDecimal cantidadProducida;

    public long getIdProduccionDetalle() { return idProduccionDetalle; }
    public void setIdProduccionDetalle(long idProduccionDetalle) { this.idProduccionDetalle = idProduccionDetalle; }
    public long getIdProduccion() { return idProduccion; }
    public void setIdProduccion(long idProduccion) { this.idProduccion = idProduccion; }
    public long getIdProducto() { return idProducto; }
    public void setIdProducto(long idProducto) { this.idProducto = idProducto; }
    public long getIdReceta() { return idReceta; }
    public void setIdReceta(long idReceta) { this.idReceta = idReceta; }
    public BigDecimal getCantidadPlanificada() { return cantidadPlanificada; }
    public void setCantidadPlanificada(BigDecimal cantidadPlanificada) { this.cantidadPlanificada = cantidadPlanificada; }
    public BigDecimal getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(BigDecimal cantidadProducida) { this.cantidadProducida = cantidadProducida; }
}
