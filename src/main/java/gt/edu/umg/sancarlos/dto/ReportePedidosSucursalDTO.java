package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.util.List;

/** Pedidos de una sucursal en un rango de entrega, sin contar los cancelados. */
public record ReportePedidosSucursalDTO(long idSucursal, String sucursal, long pedidos,
        long despachados, long pendientes, List<Producto> productos) {
    public record Producto(long idProducto, String producto, String unidad,
            BigDecimal cantidad) { }
}
