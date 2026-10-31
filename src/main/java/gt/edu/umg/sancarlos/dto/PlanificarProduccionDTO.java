package gt.edu.umg.sancarlos.dto;

import java.time.LocalDate;
import java.util.List;

/** Selección de pedidos para generar un plan. */
public record PlanificarProduccionDTO(LocalDate fechaEntrega, List<Long> idPedidos,
        String observacion) {
}
