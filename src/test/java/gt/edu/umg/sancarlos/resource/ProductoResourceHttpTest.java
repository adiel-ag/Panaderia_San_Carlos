package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.service.ProductoService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductoResourceHttpTest {

    @Test
    void buscarProductoPorIdDevuelve200CuandoExiste() throws Exception {
        Producto esperado = new Producto();
        esperado.setIdProducto(42);
        ProductoService service = new ProductoService() {
            @Override
            public Optional<Producto> buscarProductoPorId(long idProducto) {
                assertEquals(42, idProducto);
                return Optional.of(esperado);
            }
        };
        ProductoResource resource = new ProductoResource(service);

        try (Response response = resource.buscarProductoPorId("42")) {
            assertEquals(200, response.getStatus());
            assertSame(esperado, response.getEntity());
        }
        var method = ProductoResource.class.getMethod("buscarProductoPorId", String.class);
        assertEquals("{id}", method.getAnnotation(Path.class).value());
        org.junit.jupiter.api.Assertions.assertNotNull(method.getAnnotation(GET.class));
    }

    @Test
    void buscarProductoPorIdRechazaIdentificadorMalFormado() {
        ProductoResource resource = new ProductoResource(new ProductoService());

        assertThrows(BadRequestException.class, () -> resource.buscarProductoPorId("abc"));
        assertThrows(BadRequestException.class, () -> resource.buscarProductoPorId("0"));
        assertThrows(BadRequestException.class, () -> resource.buscarProductoPorId("-2"));
    }

    @Test
    void buscarProductoPorIdDevuelveNotFoundCuandoNoExiste() {
        ProductoService service = new ProductoService() {
            @Override
            public Optional<Producto> buscarProductoPorId(long idProducto) {
                return Optional.empty();
            }
        };

        assertThrows(NotFoundException.class,
                () -> new ProductoResource(service).buscarProductoPorId("42"));
    }

    @Test
    void listarProductosDevuelveRespuestaHttp200() {
        Producto esperado = new Producto();
        ProductoService service = new ProductoService() {
            @Override
            public List<Producto> listarProductos() {
                return List.of(esperado);
            }
        };

        try (Response response = new ProductoResource(service).listarProductos()) {
            assertEquals(200, response.getStatus());
            assertSame(esperado, ((List<?>) response.getEntity()).get(0));
        }
    }
}
