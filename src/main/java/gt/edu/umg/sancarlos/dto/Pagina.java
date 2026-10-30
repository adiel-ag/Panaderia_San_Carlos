package gt.edu.umg.sancarlos.dto;

import java.util.List;

/** Resultado paginado de una consulta. */
public record Pagina<T>(List<T> items, long total, int pagina, int tamano) {
}
