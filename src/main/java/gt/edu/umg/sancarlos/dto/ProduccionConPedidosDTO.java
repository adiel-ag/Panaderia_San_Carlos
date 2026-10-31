package gt.edu.umg.sancarlos.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Produccion con sus pedidos asociados. */
public record ProduccionConPedidosDTO(long idProduccion, long idUsuario, String nombreUsuario,
        LocalDateTime fechaProduccion, String estado, String observacion,
        long cantidadPedidos, List<PedidoResumenDTO> pedidos) {

    public ProduccionConPedidosDTO(ProduccionResumenDTO resumen, List<PedidoResumenDTO> pedidos) {
        this(resumen.idProduccion(), resumen.idUsuario(), resumen.nombreUsuario(),
                resumen.fechaProduccion(), resumen.estado(), resumen.observacion(),
                resumen.cantidadPedidos(), pedidos);
    }
}
