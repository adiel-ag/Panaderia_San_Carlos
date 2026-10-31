package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Total de salidas de un insumo por producción en un rango de fechas. */
public record ConsumoInsumoDTO(long idInsumo, String insumo, String unidad,
        BigDecimal consumido) { }
