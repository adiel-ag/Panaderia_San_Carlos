package gt.edu.umg.sancarlos.dto;

/** Conteos para las alertas de Inicio; 0 cuando el rol no ve esa información. */
public record AlertasDTO(long pedidosAtrasados, long productosSinReceta,
        long insumosBajoMinimo) { }
