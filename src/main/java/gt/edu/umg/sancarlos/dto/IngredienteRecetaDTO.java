package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Insumo y cantidad que consume una receta. */
public record IngredienteRecetaDTO(long idInsumo, String nombre, String unidad,
        BigDecimal cantidad) {
}
