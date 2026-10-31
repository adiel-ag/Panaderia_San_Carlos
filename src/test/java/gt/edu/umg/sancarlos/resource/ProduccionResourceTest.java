package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.dto.AsociarPedidoDTO;
import gt.edu.umg.sancarlos.dto.CantidadProducidaDTO;
import gt.edu.umg.sancarlos.dto.CrearProduccionDTO;
import gt.edu.umg.sancarlos.dto.ProduccionCreadaDTO;
import gt.edu.umg.sancarlos.dto.ProduccionConPedidosDTO;
import gt.edu.umg.sancarlos.dto.ProduccionOperacionDTO;
import gt.edu.umg.sancarlos.dto.ProduccionResumenDTO;
import gt.edu.umg.sancarlos.dto.InsumosProduccionDTO;
import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.model.ProduccionDetalle;
import gt.edu.umg.sancarlos.service.ProduccionService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProduccionResourceTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void insumosResponde200YProduccionAusente404() throws Exception {
        Method metodo = ProduccionResource.class.getMethod("consultarInsumos", String.class);
        assertNotNull(metodo.getAnnotation(GET.class));
        assertEquals("{id}/insumos", metodo.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMINISTRADOR", "GERENTE", "PRODUCCION"),
                Set.of(metodo.getAnnotation(RolesAllowed.class).value()));
        ProduccionResource recurso = new ProduccionResource(new ProduccionService() {
            @Override public InsumosProduccionDTO consultarInsumos(long id) {
                if (id == 99) throw new ProduccionNoEncontradaException("No existe", null);
                return new InsumosProduccionDTO(id, "PLANIFICADA", List.of(), List.of(), true);
            }
        });
        try (Response respuesta = recurso.consultarInsumos("5")) {
            assertEquals(200, respuesta.getStatus());
            assertEquals(5, assertInstanceOf(InsumosProduccionDTO.class,
                    respuesta.getEntity()).idProduccion());
        }
        assertThrows(BadRequestException.class, () -> recurso.consultarInsumos("0"));
        try (Response respuesta = mapper.toResponse(assertThrows(
                ProduccionNoEncontradaException.class, () -> recurso.consultarInsumos("99")))) {
            assertEquals(404, respuesta.getStatus());
        }
    }

    @Test
    void exponeRutasYMetodosHttp() throws Exception {
        assertEquals("/producciones", ProduccionResource.class.getAnnotation(Path.class).value());
        Class<?> r = ProduccionResource.class;

        Method crear = r.getMethod("crearProduccion", CrearProduccionDTO.class, UriInfo.class,
                SecurityContext.class);
        assertNotNull(crear.getAnnotation(POST.class));
        assertEquals(MediaType.APPLICATION_JSON, crear.getAnnotation(Consumes.class).value()[0]);

        Method asociar = r.getMethod("asociarPedido", String.class, AsociarPedidoDTO.class);
        assertNotNull(asociar.getAnnotation(POST.class));
        assertEquals("{id}/pedidos", asociar.getAnnotation(Path.class).value());

        Method plan = r.getMethod("generarPlan", String.class);
        assertNotNull(plan.getAnnotation(POST.class));
        assertEquals("{id}/generar-plan", plan.getAnnotation(Path.class).value());

        Method detalle = r.getMethod("consultarDetalle", String.class);
        assertNotNull(detalle.getAnnotation(GET.class));
        assertEquals("{id}/detalle", detalle.getAnnotation(Path.class).value());

        Method cantidad = r.getMethod("registrarCantidadProducida",
                String.class, String.class, CantidadProducidaDTO.class);
        assertNotNull(cantidad.getAnnotation(PUT.class));
        assertEquals("{id}/productos/{idProducto}/cantidad-producida",
                cantidad.getAnnotation(Path.class).value());

        Method finalizar = r.getMethod("finalizarProduccion", String.class, SecurityContext.class);
        assertNotNull(finalizar.getAnnotation(POST.class));
        assertEquals("{id}/finalizar", finalizar.getAnnotation(Path.class).value());
        assertNull(finalizar.getAnnotation(Consumes.class), "finalizar no necesita cuerpo");
    }

    @Test
    void creaProduccionCon201YLocation() {
        ProduccionService service = new ProduccionService() {
            @Override
            public long crearProduccion(long idUsuario, String observacion) {
                assertEquals(7, idUsuario);
                assertEquals("Turno mañana", observacion);
                return 90;
            }
        };
        CrearProduccionDTO solicitud = new CrearProduccionDTO();
        solicitud.setObservacion("Turno mañana");

        try (Response response = new ProduccionResource(service).crearProduccion(solicitud, uriInfo(),
                SesionDePrueba.usuario(7, "PRODUCCION"))) {
            assertEquals(201, response.getStatus());
            assertEquals(URI.create("http://localhost:8080/panaderia/api/producciones/90"),
                    response.getLocation());
            assertEquals(90, assertInstanceOf(ProduccionCreadaDTO.class,
                    response.getEntity()).getIdProduccion());
        }
    }

    @Test
    void asociaPedidoCon200() {
        List<Long> llamadas = new ArrayList<>();
        ProduccionService service = new ProduccionService() {
            @Override
            public void asociarPedido(long idProduccion, long idPedido) {
                llamadas.add(idProduccion);
                llamadas.add(idPedido);
            }
        };
        AsociarPedidoDTO solicitud = new AsociarPedidoDTO();
        solicitud.setIdPedido(501);

        try (Response response = new ProduccionResource(service).asociarPedido("90", solicitud)) {
            assertEquals(200, response.getStatus());
            assertEquals(90, assertInstanceOf(ProduccionOperacionDTO.class,
                    response.getEntity()).getIdProduccion());
        }
        assertEquals(List.of(90L, 501L), llamadas);
    }

    @Test
    void generaPlanYDevuelveDetalleDeOracle() {
        List<ProduccionDetalle> detalle = List.of(detalle());
        List<String> orden = new ArrayList<>();
        ProduccionService service = new ProduccionService() {
            @Override
            public void generarPlan(long idProduccion) {
                orden.add("generar");
            }

            @Override
            public List<ProduccionDetalle> consultarDetalle(long idProduccion) {
                orden.add("consultar");
                return detalle;
            }
        };

        try (Response response = new ProduccionResource(service).generarPlan("90")) {
            assertEquals(200, response.getStatus());
            assertSame(detalle, response.getEntity());
        }
        assertEquals(List.of("generar", "consultar"), orden);
    }

    @Test
    void consultaDetalleCon200ListaVaciaY404SiNoExiste() {
        ProduccionService service = new ProduccionService() {
            @Override
            public List<ProduccionDetalle> consultarDetalle(long idProduccion) {
                if (idProduccion == 404) {
                    throw new ProduccionNoEncontradaException("No existe una producción con ID 404", null);
                }
                return List.of();
            }
        };
        ProduccionResource resource = new ProduccionResource(service);

        try (Response response = resource.consultarDetalle("90")) {
            assertEquals(200, response.getStatus());
            assertTrue(((List<?>) response.getEntity()).isEmpty());
        }
        ProduccionNoEncontradaException exception = assertThrows(
                ProduccionNoEncontradaException.class, () -> resource.consultarDetalle("404"));
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(404, response.getStatus());
        }
    }

    @Test
    void reemplazaCantidadProducidaCon200() {
        List<Object> llamadas = new ArrayList<>();
        ProduccionService service = new ProduccionService() {
            @Override
            public void registrarProducido(long idProduccion, long idProducto, BigDecimal cantidad) {
                llamadas.add(idProduccion);
                llamadas.add(idProducto);
                llamadas.add(cantidad);
            }
        };

        try (Response response = new ProduccionResource(service)
                .registrarCantidadProducida("90", "35", cantidad("12.500"))) {
            assertEquals(200, response.getStatus());
        }
        assertEquals(List.of(90L, 35L, new BigDecimal("12.500")), llamadas);
    }

    @Test
    void finalizaProduccionCon200() {
        List<Long> llamadas = new ArrayList<>();
        ProduccionService service = new ProduccionService() {
            @Override
            public void finalizarProduccion(long idProduccion, long idUsuario) {
                llamadas.add(idProduccion);
                llamadas.add(idUsuario);
            }
        };
        try (Response response = new ProduccionResource(service).finalizarProduccion("90",
                SesionDePrueba.usuario(7, "PRODUCCION"))) {
            assertEquals(200, response.getStatus());
        }
        assertEquals(List.of(90L, 7L), llamadas);
    }

    @Test
    void rechazaIdsCuerposYCantidadesInvalidasCon400() {
        ProduccionResource resource = new ProduccionResource(new ProduccionService());

        assertThrows(BadRequestException.class, () -> resource.consultarDetalle("abc"));
        assertThrows(BadRequestException.class, () -> resource.consultarDetalle("0"));
        assertThrows(BadRequestException.class, () -> resource.generarPlan("-1"));
        assertThrows(BadRequestException.class, () -> resource.asociarPedido("90", null));
        assertThrows(BadRequestException.class,
                () -> resource.finalizarProduccion("abc", SesionDePrueba.usuario(7, "PRODUCCION")));
        assertThrows(NoAutenticadoException.class, () -> resource.finalizarProduccion("90", null));
        assertThrows(BadRequestException.class,
                () -> resource.crearProduccion(null, uriInfo(), SesionDePrueba.usuario(7, "PRODUCCION")));
        assertThrows(BadRequestException.class,
                () -> resource.registrarCantidadProducida("90", "x", cantidad("1")));
        assertThrows(BadRequestException.class,
                () -> resource.registrarCantidadProducida("90", "35", null));
        assertThrows(BadRequestException.class,
                () -> resource.registrarCantidadProducida("90", "35", new CantidadProducidaDTO()));
        assertThrows(BadRequestException.class,
                () -> resource.registrarCantidadProducida("90", "35", cantidad("0")));
        assertThrows(BadRequestException.class,
                () -> resource.registrarCantidadProducida("90", "35", cantidad("-3")));

        try (Response response = mapper.toResponse(new BadRequestException("x"))) {
            assertEquals(400, response.getStatus());
        }
    }

    @Test
    void conflictoDeOracleSeConvierteEn409SinTextoSql() {
        ProduccionService service = new ProduccionService() {
            @Override
            public void finalizarProduccion(long idProduccion, long idUsuario) {
                throw new ProduccionConflictoException("Inventario insuficiente para finalizar la producción",
                        new SQLException("ORA-20003: Inventario insuficiente ORA-06512", "72000", 20003));
            }
        };
        ProduccionConflictoException exception = assertThrows(ProduccionConflictoException.class,
                () -> new ProduccionResource(service).finalizarProduccion("90",
                        SesionDePrueba.usuario(7, "PRODUCCION")));
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(409, response.getStatus());
            String message = ((ApiError) response.getEntity()).getMessage();
            assertFalse(message.contains("ORA-"));
        }
    }

    @Test
    void cancelarProduccionEsPostParaAdministradorYProduccion() throws Exception {
        Method cancelar = ProduccionResource.class.getMethod("cancelarProduccion", String.class);
        assertNotNull(cancelar.getAnnotation(POST.class));
        assertEquals("{id}/cancelar", cancelar.getAnnotation(Path.class).value());
        assertNull(cancelar.getAnnotation(Consumes.class), "cancelar no necesita cuerpo");
        assertEquals(Set.of(Roles.ADMINISTRADOR, Roles.PRODUCCION),
                Set.of(cancelar.getAnnotation(RolesAllowed.class).value()));
    }

    @Test
    void cancelaProduccionCon200Y409O404SegunElCaso() {
        ProduccionResource resource = new ProduccionResource(new ProduccionService() {
            @Override
            public ProduccionConPedidosDTO cancelarProduccion(long idProduccion) {
                if (idProduccion == 404) {
                    throw new ProduccionNoEncontradaException("No existe una producción con ID 404", null);
                }
                if (idProduccion == 409) {
                    throw new ProduccionConflictoException(
                            "Solo se puede cancelar una producción planificada", null);
                }
                return new ProduccionConPedidosDTO(new ProduccionResumenDTO(idProduccion, 1,
                        "Usuario", LocalDateTime.of(2026, 10, 31, 8, 0), "CANCELADA", null, 0),
                        List.of());
            }
        });

        try (Response response = resource.cancelarProduccion("90")) {
            assertEquals(200, response.getStatus());
            assertEquals("CANCELADA", assertInstanceOf(ProduccionConPedidosDTO.class,
                    response.getEntity()).estado());
        }
        try (Response response = mapper.toResponse(assertThrows(ProduccionConflictoException.class,
                () -> resource.cancelarProduccion("409")))) {
            assertEquals(409, response.getStatus());
        }
        try (Response response = mapper.toResponse(assertThrows(ProduccionNoEncontradaException.class,
                () -> resource.cancelarProduccion("404")))) {
            assertEquals(404, response.getStatus());
        }
        assertThrows(BadRequestException.class, () -> resource.cancelarProduccion("x"));
    }

    private static CantidadProducidaDTO cantidad(String valor) {
        CantidadProducidaDTO dto = new CantidadProducidaDTO();
        dto.setCantidadProducida(new BigDecimal(valor));
        return dto;
    }

    private static ProduccionDetalle detalle() {
        ProduccionDetalle detalle = new ProduccionDetalle();
        detalle.setIdProduccionDetalle(1);
        detalle.setIdProduccion(90);
        detalle.setIdProducto(35);
        detalle.setIdReceta(4);
        detalle.setCantidadPlanificada(new BigDecimal("12.000"));
        detalle.setCantidadProducida(BigDecimal.ZERO);
        return detalle;
    }

    private static UriInfo uriInfo() {
        return (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[]{UriInfo.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAbsolutePathBuilder")) {
                        return UriBuilder.fromUri("http://localhost:8080/panaderia/api/producciones");
                    }
                    return null;
                });
    }
}
