package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.PedidoDetalle;
import gt.edu.umg.sancarlos.service.PedidoService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoResourceTest {

    @Test
    void exponeCreacionYConsultaPorId() throws Exception {
        assertEquals("/pedidos", PedidoResource.class.getAnnotation(Path.class).value());
        var create = PedidoResource.class.getMethod("crearPedido", CrearPedidoDTO.class, UriInfo.class);
        var find = PedidoResource.class.getMethod("buscarPedidoPorId", String.class);
        assertNotNull(create.getAnnotation(POST.class));
        assertNotNull(find.getAnnotation(GET.class));
        assertEquals("{id}", find.getAnnotation(Path.class).value());
    }

    @Test
    void creaPedidoCon201YLocation() {
        PedidoService service = new PedidoService() {
            @Override
            public long crearPedido(CrearPedidoDTO solicitud) {
                return 501;
            }
        };
        CrearPedidoDTO request = new CrearPedidoDTO();

        try (Response response = new PedidoResource(service).crearPedido(request, uriInfo())) {
            assertEquals(201, response.getStatus());
            assertEquals(URI.create("http://localhost:8080/panaderia/api/pedidos/501"),
                    response.getLocation());
        }
    }

    @Test
    void consultaPedidoYDetallesCon200() {
        Pedido pedido = pedidoConDetalle();
        PedidoService service = new PedidoService() {
            @Override
            public Optional<Pedido> buscarPedidoPorId(long idPedido) {
                assertEquals(501, idPedido);
                return Optional.of(pedido);
            }
        };

        try (Response response = new PedidoResource(service).buscarPedidoPorId("501")) {
            assertEquals(200, response.getStatus());
            Pedido body = assertInstanceOf(Pedido.class, response.getEntity());
            assertEquals("PAN", body.getDetalles().get(0).getNombreProducto());
        }
    }

    @Test
    void validaIdYReportaPedidoInexistente() {
        PedidoService emptyService = new PedidoService() {
            @Override
            public Optional<Pedido> buscarPedidoPorId(long idPedido) {
                return Optional.empty();
            }
        };
        PedidoResource resource = new PedidoResource(emptyService);

        assertThrows(BadRequestException.class, () -> resource.buscarPedidoPorId("abc"));
        assertThrows(BadRequestException.class, () -> resource.buscarPedidoPorId("0"));
        assertThrows(NotFoundException.class, () -> resource.buscarPedidoPorId("501"));
    }

    private static Pedido pedidoConDetalle() {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(501);
        pedido.setIdSucursal(2);
        pedido.setIdUsuario(7);
        pedido.setFechaEntrega(LocalDate.of(2026, 10, 25));
        pedido.setEstado("CONFIRMADO");
        PedidoDetalle detalle = new PedidoDetalle();
        detalle.setIdPedidoDetalle(3);
        detalle.setIdProducto(35);
        detalle.setNombreProducto("PAN");
        detalle.setCantidad(new BigDecimal("4.000"));
        pedido.setDetalles(List.of(detalle));
        return pedido;
    }

    private static UriInfo uriInfo() {
        return (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[]{UriInfo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAbsolutePathBuilder")) {
                        return UriBuilder.fromUri("http://localhost:8080/panaderia/api/pedidos");
                    }
                    return null;
                });
    }
}
