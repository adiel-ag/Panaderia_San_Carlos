package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un producto final activo y su receta vigente. Los datos de la receta solo se
 * llenan si hay exactamente una vigente; recetasVigentes distingue 0 de varias.
 */
public record RecetaResumenDTO(long idProducto, String nombreProducto, String unidad,
        Long idReceta, Integer version, BigDecimal rendimiento, int cantidadIngredientes,
        LocalDate fechaInicio, int recetasVigentes) {
}
