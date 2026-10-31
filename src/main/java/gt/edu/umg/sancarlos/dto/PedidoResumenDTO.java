package gt.edu.umg.sancarlos.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Cabecera de pedido para los listados. */
public record PedidoResumenDTO(long idPedido, long idSucursal, String nombreSucursal,
        long idUsuario, String nombreUsuario, LocalDateTime fechaPedido,
        LocalDate fechaEntrega, String estado, String observacion, long cantidadProductos) {
}
