package gt.edu.umg.sancarlos.model;

import java.math.BigDecimal;

/** Representa un producto y su cantidad dentro de un pedido. */
public class PedidoDetalle {

    private long idPedidoDetalle;
    private long idPedido;
    private long idProducto;
    private String nombreProducto;
    private BigDecimal cantidad;

    public long getIdPedidoDetalle() { return idPedidoDetalle; }
    public void setIdPedidoDetalle(long idPedidoDetalle) { this.idPedidoDetalle = idPedidoDetalle; }
    public long getIdPedido() { return idPedido; }
    public void setIdPedido(long idPedido) { this.idPedido = idPedido; }
    public long getIdProducto() { return idProducto; }
    public void setIdProducto(long idProducto) { this.idProducto = idProducto; }
    public String getNombreProducto() { return nombreProducto; }
    public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
}
