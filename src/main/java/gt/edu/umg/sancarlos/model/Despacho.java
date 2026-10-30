package gt.edu.umg.sancarlos.model;

import java.time.LocalDateTime;

/** Representa el despacho registrado por PKG_DESPACHO para un pedido. */
public class Despacho {

    private long idDespacho;
    private long idPedido;
    private Long idSucursal;
    private long idUsuario;
    private LocalDateTime fechaDespacho;
    private String estado;
    private String observacion;

    public long getIdDespacho() { return idDespacho; }
    public void setIdDespacho(long idDespacho) { this.idDespacho = idDespacho; }
    public long getIdPedido() { return idPedido; }
    public void setIdPedido(long idPedido) { this.idPedido = idPedido; }
    public Long getIdSucursal() { return idSucursal; }
    public void setIdSucursal(Long idSucursal) { this.idSucursal = idSucursal; }
    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }
    public LocalDateTime getFechaDespacho() { return fechaDespacho; }
    public void setFechaDespacho(LocalDateTime fechaDespacho) { this.fechaDespacho = fechaDespacho; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
