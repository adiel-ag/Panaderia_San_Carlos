package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Insumo y cantidad de una receta nueva. */
public record NuevoIngredienteDTO(Long idInsumo, BigDecimal cantidad) {
}
