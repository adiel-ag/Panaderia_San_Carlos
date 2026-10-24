package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Datos de entrada para añadir un producto al pedido. */
public class CrearPedidoDetalleDTO {

    private long idProducto;
    private BigDecimal cantidad;

    public CrearPedidoDetalleDTO() {
    }

    public CrearPedidoDetalleDTO(long idProducto, int cantidad) {
        this.idProducto = idProducto;
        this.cantidad = BigDecimal.valueOf(cantidad);
    }

    public long getIdProducto() { return idProducto; }
    public void setIdProducto(long idProducto) { this.idProducto = idProducto; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
}
