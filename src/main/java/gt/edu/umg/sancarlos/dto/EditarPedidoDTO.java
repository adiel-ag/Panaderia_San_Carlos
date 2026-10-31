package gt.edu.umg.sancarlos.dto;

import java.time.LocalDate;
import java.util.List;

/** Datos editables de un pedido existente. */
public record EditarPedidoDTO(LocalDate fechaEntrega, String observacion,
        List<CrearPedidoDetalleDTO> detalles) {
}
