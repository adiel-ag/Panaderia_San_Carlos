package gt.edu.umg.sancarlos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Datos de entrada necesarios para solicitar la creación de un pedido. */
public class CrearPedidoDTO {

    private long idSucursal;
    /** Lo fija el servidor con el usuario de la sesion; si llega en el JSON se ignora. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long idUsuario;
    private LocalDate fechaEntrega;
    private String observacion;
    private List<CrearPedidoDetalleDTO> detalles = new ArrayList<>();

    public long getIdSucursal() { return idSucursal; }
    public void setIdSucursal(long idSucursal) { this.idSucursal = idSucursal; }
    public long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(long idUsuario) { this.idUsuario = idUsuario; }
    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public List<CrearPedidoDetalleDTO> getDetalles() { return detalles; }
    public void setDetalles(List<CrearPedidoDetalleDTO> detalles) {
        this.detalles = detalles == null ? new ArrayList<>() : new ArrayList<>(detalles);
    }
}
