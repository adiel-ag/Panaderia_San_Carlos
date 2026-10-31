package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Existencia de un producto activo en el listado. */
public record ExistenciaResumenDTO(long idProducto, String nombre, String tipo, String unidad,
        BigDecimal existencia, BigDecimal stockMinimo, Boolean bajoMinimo) {
}
