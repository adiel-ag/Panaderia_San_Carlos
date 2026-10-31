package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Producciones finalizadas de un día, con lo planificado y lo producido por producto. */
public record ReporteProduccionDiaDTO(LocalDate fecha, long producciones,
        List<Producto> productos) {
    public record Producto(long idProducto, String producto, String unidad,
            BigDecimal planificado, BigDecimal producido) { }
}
