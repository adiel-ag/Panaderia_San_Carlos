package gt.edu.umg.sancarlos.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Representa un pedido ya creado en el dominio de la aplicación. */
public class Pedido {

    private long idPedido;
    private long idSucursal;
    private long idUsuario;
    private LocalDate fechaEntrega;
    private String estado;
    private String observacion;
    /** Produccion a la que esta asociado; null si no esta en ninguna. */
    private Long idProduccion;
    private List<PedidoDetalle> detalles = new ArrayList<>();

    public long getIdPedido() { return idPedido; }
    public void setIdPedido(long idPedido) { this.idPedido = idPedido; }
    public long getIdSucursal() { return idSucursal; }
    public void setIdSucursal(long idSucursal) { this.idSucursal = idSucursal; }
    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public Long getIdProduccion() { return idProduccion; }
    public void setIdProduccion(Long idProduccion) { this.idProduccion = idProduccion; }
    public List<PedidoDetalle> getDetalles() { return detalles; }
    public void setDetalles(List<PedidoDetalle> detalles) {
        this.detalles = detalles == null ? new ArrayList<>() : new ArrayList<>(detalles);
    }
}
