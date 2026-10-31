package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sancarlos.dto.ExistenciaResumenDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.PedidoResumenDTO;
import gt.edu.umg.sancarlos.dto.ProduccionResumenDTO;
import gt.edu.umg.sancarlos.dto.ProduccionConPedidosDTO;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.InventarioService;
import gt.edu.umg.sancarlos.service.PedidoService;
import gt.edu.umg.sancarlos.service.ProduccionService;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListadosResourceTest {

    private static final URI BASE = URI.create("http://localhost/api/");

    @Test
    void aplicaAlcanceDeSucursalAntesDeConsultar() throws Exception {
        List<Long> recibidas = new ArrayList<>();
        PedidoService servicio = new PedidoService() {
            @Override public Pagina<PedidoResumenDTO> listar(int pagina, int tamano,
                    String estado, Long idSucursal, LocalDate desde, LocalDate hasta) {
                recibidas.add(idSucursal);
                return new Pagina<>(List.of(), 0, pagina, tamano);
            }
        };
        ApplicationHandler sucursal = aplicacion(Roles.SUCURSAL, 2L, servicio,
                new ProduccionService(), new InventarioService());
        assertEquals(200, estado(sucursal, "pedidos"));
        assertEquals(List.of(2L), recibidas);
        assertEquals(200, estado(sucursal, "pedidos?idSucursal=1"));
        assertEquals(List.of(2L), recibidas);
        Pagina<?> vacia = (Pagina<?>) respuesta(sucursal, "pedidos?idSucursal=1").getEntity();
        assertEquals(List.of(), vacia.items());
        assertEquals(0, vacia.total());
        assertEquals(List.of(2L), recibidas);
        assertEquals(200, estado(sucursal, "pedidos?idSucursal=2"));
        assertEquals(List.of(2L, 2L), recibidas);

        ApplicationHandler sinSucursal = aplicacion(Roles.SUCURSAL, null, servicio,
                new ProduccionService(), new InventarioService());
        assertEquals(200, estado(sinSucursal, "pedidos"));
        assertEquals(List.of(2L, 2L), recibidas);

        ApplicationHandler admin = aplicacion(Roles.ADMINISTRADOR, null, servicio,
                new ProduccionService(), new InventarioService());
        assertEquals(200, estado(admin, "pedidos?idSucursal=1"));
        assertEquals(200, estado(admin, "pedidos"));
        assertEquals(java.util.Arrays.asList(2L, 2L, 1L, null), recibidas);
    }

    @Test
    void rechazaFiltrosInvalidosSinConsultarServicios() throws Exception {
        PedidoService pedidos = new PedidoService() {
            @Override public Pagina<PedidoResumenDTO> listar(int pagina, int tamano,
                    String estado, Long idSucursal, LocalDate desde, LocalDate hasta) {
                throw new AssertionError("No debe consultar pedidos");
            }
        };
        ProduccionService producciones = new ProduccionService() {
            @Override public Pagina<ProduccionResumenDTO> listar(int pagina, int tamano, String estado) {
                throw new AssertionError("No debe consultar producciones");
            }
        };
        InventarioService inventario = new InventarioService() {
            @Override public Pagina<ExistenciaResumenDTO> listarExistencias(int pagina, int tamano,
                    String tipo, String nombre, Boolean bajoMinimo) {
                throw new AssertionError("No debe consultar inventario");
            }
        };
        ApplicationHandler handler = aplicacion(Roles.ADMINISTRADOR, null, pedidos,
                producciones, inventario);
        for (String ruta : List.of("pedidos?estado=OTRO", "pedidos?idSucursal=0",
                "pedidos?entregaDesde=ayer", "pedidos?entregaHasta=2026-02-30",
                "pedidos?entregaDesde=2026-10-31&entregaHasta=2026-10-30",
                "pedidos?pagina=0", "pedidos?tamano=101", "producciones?estado=OTRO",
                "producciones?pagina=0", "inventario/existencias?tipo=OTRO",
                "inventario/existencias?bajoMinimo=si",
                "inventario/existencias?nombre=" + "a".repeat(101),
                "inventario/existencias?tamano=101")) {
            assertEquals(400, estado(handler, ruta), ruta);
        }
    }

    @Test
    void produccionInexistenteResponde404() throws Exception {
        ProduccionService producciones = new ProduccionService() {
            @Override public ProduccionConPedidosDTO buscarPorId(long idProduccion) {
                throw new ProduccionNoEncontradaException(
                        "No existe una producción con ID " + idProduccion, null);
            }
        };
        ApplicationHandler handler = aplicacion(Roles.ADMINISTRADOR, null,
                new PedidoService(), producciones, new InventarioService());
        assertEquals(404, estado(handler, "producciones/8"));
        assertEquals(400, estado(handler, "producciones/0"));
    }

    @Test
    void losResumenesTienenSoloLosCamposPublicos() throws Exception {
        ObjectMapper mapper = new JacksonObjectMapperProvider().getContext(Object.class);
        PedidoResumenDTO pedido = new PedidoResumenDTO(1, 2, "Centro", 3, "Ana",
                LocalDateTime.of(2026, 10, 30, 10, 0), LocalDate.of(2026, 11, 1),
                "PENDIENTE", null, 4);
        ProduccionResumenDTO produccion = new ProduccionResumenDTO(5, 3, "Ana",
                LocalDateTime.of(2026, 10, 30, 11, 0), "PLANIFICADA", null, 1);
        ExistenciaResumenDTO existencia = new ExistenciaResumenDTO(6, "Harina", "INSUMO",
                "kg", null, new BigDecimal("5.000"), null);
        assertEquals(Set.of("idPedido", "idSucursal", "nombreSucursal", "idUsuario",
                "nombreUsuario", "fechaPedido", "fechaEntrega", "estado", "observacion",
                "cantidadProductos"), campos(mapper.valueToTree(pedido)));
        assertEquals(Set.of("idProduccion", "idUsuario", "nombreUsuario",
                "fechaProduccion", "estado", "observacion", "cantidadPedidos"),
                campos(mapper.valueToTree(produccion)));
        JsonNode detalle = mapper.valueToTree(new ProduccionConPedidosDTO(
                produccion, List.of(pedido)));
        assertEquals(Set.of("idProduccion", "idUsuario", "nombreUsuario",
                "fechaProduccion", "estado", "observacion", "cantidadPedidos", "pedidos"),
                campos(detalle));
        assertEquals(campos(mapper.valueToTree(pedido)), campos(detalle.get("pedidos").get(0)));
        assertEquals(Set.of("idProducto", "nombre", "tipo", "unidad", "existencia",
                "stockMinimo", "bajoMinimo"), campos(mapper.valueToTree(existencia)));
        assertNull(mapper.valueToTree(existencia).get("existencia").textValue());
    }

    private static Set<String> campos(JsonNode json) {
        return StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(
                json.fieldNames(), 0), false).collect(Collectors.toSet());
    }

    private static int estado(ApplicationHandler handler, String ruta) throws Exception {
        return respuesta(handler, ruta).getStatus();
    }

    private static ContainerResponse respuesta(ApplicationHandler handler, String ruta)
            throws Exception {
        ContainerRequest request = new ContainerRequest(BASE, BASE.resolve(ruta), "GET", null,
                new MapPropertiesDelegate(), null);
        return handler.apply(request).get();
    }

    private static ApplicationHandler aplicacion(String rol, Long sucursal, PedidoService pedidos,
            ProduccionService producciones, InventarioService inventario) {
        return new ApplicationHandler(new ResourceConfig()
                .register(new PedidoResource(pedidos))
                .register(new ProduccionResource(producciones))
                .register(new InventarioResource(inventario))
                .register(new Sesion(rol, sucursal))
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
    }

    @Priority(Priorities.AUTHENTICATION)
    private record Sesion(String rol, Long sucursal) implements ContainerRequestFilter {
        @Override public void filter(ContainerRequestContext contexto) {
            contexto.setSecurityContext(new UsuarioSecurityContext(new UsuarioSesion(
                    1, "u", "Usuario", rol, sucursal), false));
        }
    }
}
