package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Datos para guardar una nueva version de la receta de un producto. */
public record NuevaRecetaDTO(Long idProducto, BigDecimal rendimiento,
        List<NuevoIngredienteDTO> ingredientes) {
}
