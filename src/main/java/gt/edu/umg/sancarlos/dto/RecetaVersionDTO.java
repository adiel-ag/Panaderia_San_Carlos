package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una version del historial de recetas de un producto. */
public record RecetaVersionDTO(long idReceta, int version, BigDecimal rendimiento,
        String estado, LocalDate fechaInicio, LocalDate fechaFin, boolean vigente,
        int cantidadIngredientes) {
}
