package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.dto.DespacharPedidoDTO;
import gt.edu.umg.sancarlos.exception.DespachoConflictoException;
import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.service.DespachoService;
import gt.edu.umg.sancarlos.service.PedidoService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DespachoResourceTest {

    private static final URI DESPACHO_URI =
            URI.create("http://localhost:8080/panaderia/api/pedidos/501/despacho");

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void exponeRutasYMetodosHttp() throws Exception {
        assertEquals("/pedidos/{id}/despacho", DespachoResource.class.getAnnotation(Path.class).value());
        Method despachar = DespachoResource.class.getMethod("despacharPedido",
                String.class, DespacharPedidoDTO.class, UriInfo.class, SecurityContext.class);
        assertNotNull(despachar.getAnnotation(POST.class));
        assertEquals(MediaType.APPLICATION_JSON, despachar.getAnnotation(Consumes.class).value()[0]);
        assertNotNull(DespachoResource.class.getMethod("buscarDespacho", String.class)
                .getAnnotation(GET.class));
    }

    @Test
    void despachaPedidoCon201LocationYDespacho() {
        Despacho despacho = despacho();
        List<Object> llamadas = new ArrayList<>();
        DespachoService service = new DespachoService() {
            @Override
            public Despacho despacharPedido(long idPedido, long idUsuario, String observacion) {
                llamadas.add(idPedido);
                llamadas.add(idUsuario);
                llamadas.add(observacion);
                return despacho;
            }
        };
        DespacharPedidoDTO solicitud = new DespacharPedidoDTO();
        solicitud.setObservacion("Ruta norte");

        try (Response response = new DespachoResource(service)
                .despacharPedido("501", solicitud, uriInfo(), SesionDePrueba.usuario(7, "DESPACHO"))) {
            assertEquals(201, response.getStatus());
            assertEquals(DESPACHO_URI, response.getLocation());
            assertSame(despacho, response.getEntity());
        }
        assertEquals(List.of(501L, 7L, "Ruta norte"), llamadas);
    }

    @Test
    void despacharSinCuerpoUsaObservacionNulaYElUsuarioDeLaSesion() {
        List<Object> llamadas = new ArrayList<>();
        DespachoService service = new DespachoService() {
            @Override
            public Despacho despacharPedido(long idPedido, long idUsuario, String observacion) {
                llamadas.add(idUsuario);
                llamadas.add(observacion);
                return despacho();
            }
        };

        try (Response response = new DespachoResource(service)
                .despacharPedido("501", null, uriInfo(), SesionDePrueba.usuario(9, "DESPACHO"))) {
            assertEquals(201, response.getStatus());
        }
        assertEquals(java.util.Arrays.asList(9L, null), llamadas);
    }

    @Test
    void consultaDespachoCon200O404() {
        Despacho despacho = despacho();
        DespachoService service = new DespachoService() {
            @Override
            public Optional<Despacho> buscarPorPedido(long idPedido) {
                return idPedido == 501 ? Optional.of(despacho) : Optional.empty();
            }
        };
        DespachoResource resource = new DespachoResource(service);

        try (Response response = resource.buscarDespacho("501")) {
            assertEquals(200, response.getStatus());
            assertSame(despacho, response.getEntity());
        }
        assertThrows(NotFoundException.class, () -> resource.buscarDespacho("502"));
    }

    @Test
    void rechazaIdYCuerpoInvalidosCon400() {
        DespachoResource resource = new DespachoResource(new DespachoService());

        assertThrows(BadRequestException.class, () -> resource.buscarDespacho("abc"));
        assertThrows(BadRequestException.class, () -> resource.buscarDespacho("0"));
        assertThrows(BadRequestException.class,
                () -> resource.despacharPedido("-1", new DespacharPedidoDTO(), uriInfo(), SesionDePrueba.usuario(7, "DESPACHO")));
        assertThrows(NoAutenticadoException.class,
                () -> resource.despacharPedido("501", null, uriInfo(), null));
    }

    @Test
    void pedidoNoListoResponde409SinTextoSql() {
        DespachoService service = new DespachoService() {
            @Override
            public Despacho despacharPedido(long idPedido, long idUsuario, String observacion) {
                throw new DespachoConflictoException("El pedido aún no está listo para despacho",
                        new SQLException("ORA-20501: no listo ORA-06512", "72000", 20501));
            }
        };
        DespacharPedidoDTO solicitud = new DespacharPedidoDTO();

        DespachoConflictoException exception = assertThrows(DespachoConflictoException.class,
                () -> new DespachoResource(service).despacharPedido("501", solicitud, uriInfo(),
                        SesionDePrueba.usuario(7, "DESPACHO")));
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(409, response.getStatus());
            assertFalse(((ApiError) response.getEntity()).getMessage().contains("ORA-"));
        }
    }

    @Test
    void jerseyEnrutaDespachoYPedidoASusRecursos() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(501);
        PedidoService pedidoService = new PedidoService() {
            @Override
            public Optional<Pedido> buscarPedidoPorId(long idPedido) {
                return Optional.of(pedido);
            }
        };
        DespachoService despachoService = new DespachoService() {
            @Override
            public Optional<Despacho> buscarPorPedido(long idPedido) {
                return Optional.of(despacho());
            }
        };
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig()
                .register(new PedidoResource(pedidoService))
                .register(new DespachoResource(despachoService))
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));

        ContainerResponse despacho = get(handler, "pedidos/501/despacho");
        assertEquals(200, despacho.getStatus());
        assertTrue(despacho.getEntity() instanceof Despacho);

        ContainerResponse pedidoResponse = get(handler, "pedidos/501");
        assertEquals(200, pedidoResponse.getStatus());
        assertSame(pedido, pedidoResponse.getEntity());
    }

    @Test
    void serializaFechaDeDespachoEnFormatoIso() throws Exception {
        ObjectMapper objectMapper = new JacksonObjectMapperProvider().getContext(Despacho.class);
        String json = objectMapper.writeValueAsString(despacho());
        assertTrue(json.contains("\"fechaDespacho\":\"2026-10-27T10:30:00\""), json);
        assertTrue(json.contains("\"estado\":\"DESPACHADO\""), json);
    }

    private static ContainerResponse get(ApplicationHandler handler, String ruta) throws Exception {
        URI base = URI.create("http://localhost/api/");
        ContainerRequest request = new ContainerRequest(base, base.resolve(ruta), "GET",
                null, new org.glassfish.jersey.internal.MapPropertiesDelegate(), null);
        return handler.apply(request).get();
    }

    private static Despacho despacho() {
        Despacho despacho = new Despacho();
        despacho.setIdDespacho(12);
        despacho.setIdPedido(501);
        despacho.setIdUsuario(7);
        despacho.setFechaDespacho(LocalDateTime.of(2026, 10, 27, 10, 30));
        despacho.setEstado("DESPACHADO");
        despacho.setObservacion("Ruta norte");
        return despacho;
    }

    private static UriInfo uriInfo() {
        return (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[]{UriInfo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAbsolutePath")) {
                        return DESPACHO_URI;
                    }
                    return null;
                });
    }
}
