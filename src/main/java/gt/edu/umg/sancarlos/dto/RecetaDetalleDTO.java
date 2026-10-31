package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Una version de receta con sus ingredientes ordenados por nombre. */
public record RecetaDetalleDTO(long idReceta, long idProducto, String nombreProducto,
        String unidad, int version, BigDecimal rendimiento, String estado,
        LocalDate fechaInicio, LocalDate fechaFin, boolean vigente,
        List<IngredienteRecetaDTO> ingredientes) {

    public RecetaDetalleDTO {
        ingredientes = ingredientes == null ? List.of() : List.copyOf(ingredientes);
    }
}
