package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.service.ProductoService;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestResourcesTest {

    @Test
    void restApplicationIsMountedAtApi() {
        assertEquals("/api", RestApplication.class.getAnnotation(ApplicationPath.class).value());
    }

    @Test
    void healthResourceReturnsExpectedStatus() {
        assertEquals(Map.of("status", "ok"), new HealthResource().health());
        assertEquals("/health", HealthResource.class.getAnnotation(Path.class).value());
    }

    @Test
    void productosResourceDelegatesListingToService() throws NoSuchMethodException {
        Producto esperado = new Producto();
        ProductoService serviceFalso = new ProductoService() {
            @Override
            public List<Producto> listarProductos() {
                return List.of(esperado);
            }
        };
        ProductoResource resource = new ProductoResource(serviceFalso);

        Response response = (Response) resource.listarProductos();
        assertEquals(200, response.getStatus());
        assertSame(esperado, ((List<?>) response.getEntity()).get(0));
        response.close();
        assertEquals("/productos", ProductoResource.class.getAnnotation(Path.class).value());
        assertTrue(ProductoResource.class.getMethod("listarProductos").isAnnotationPresent(GET.class));
    }
}



