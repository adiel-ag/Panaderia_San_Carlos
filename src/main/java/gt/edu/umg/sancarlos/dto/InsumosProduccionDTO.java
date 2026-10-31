package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.util.List;

public record InsumosProduccionDTO(long idProduccion, String estado, List<Insumo> insumos,
        List<ProductoSinReceta> productosSinReceta, boolean suficiente) {
    public record Insumo(long idInsumo, String nombre, String unidad, BigDecimal requerido,
            BigDecimal disponible, BigDecimal faltante, boolean sinInventario) { }
    public record ProductoSinReceta(long idProducto, String nombre) { }
}
