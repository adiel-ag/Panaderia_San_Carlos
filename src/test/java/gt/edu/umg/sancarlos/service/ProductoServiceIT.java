package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.model.Producto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoServiceIT {

    private final ProductoService productoService = new ProductoService();

    @Test
    void listsAndPrintsProductsFromOracle() {
        List<Producto> productos = productoService.listarProductos();

        assertNotNull(productos);
        if (productos.isEmpty()) {
            System.out.println("Oracle no devolvió productos.");
            return;
        }

        productos.forEach(producto -> System.out.printf(
                "ID=%d | Producto=%s | Tipo=%s | Unidad=%s (%s) | "
                        + "Stock mínimo=%s | Estado=%s%n",
                producto.getIdProducto(),
                producto.getNombre(),
                producto.getNombreTipoProducto(),
                producto.getNombreUnidadMedida(),
                producto.getAbreviaturaUnidadMedida(),
                producto.getStockMinimo(),
                producto.getEstado()
        ));
    }

    @Test
    void findsAProductByIdFromTheListedProducts() {
        List<Producto> productos = productoService.listarProductos();
        Assumptions.assumeTrue(
                !productos.isEmpty(),
                "La prueba necesita al menos un producto en Oracle"
        );

        long idProducto = productos.get(0).getIdProducto();
        Optional<Producto> producto = productoService.buscarProductoPorId(idProducto);

        assertTrue(producto.isPresent(), "Debe encontrar el producto listado por su ID");
        System.out.printf(
                "Producto encontrado por ID: %d | %s%n",
                producto.orElseThrow().getIdProducto(),
                producto.orElseThrow().getNombre()
        );
    }
}
