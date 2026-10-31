package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.EditarPedidoDTO;
import gt.edu.umg.sancarlos.dto.PlanificarProduccionDTO;
import gt.edu.umg.sancarlos.dto.RecepcionPedidoDTO;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.DespachoService;
import gt.edu.umg.sancarlos.service.PedidoService;
import gt.edu.umg.sancarlos.service.ProduccionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Contratos REST del flujo diario sin conexión a la base. */
class FlujoDiarioResourceTest {
    @Test void planificaConUsuarioDeSesionYPermiteFechasDiferentes() throws Exception {
        var metodo = ProduccionResource.class.getMethod("planificar", PlanificarProduccionDTO.class,
                UriInfo.class, jakarta.ws.rs.core.SecurityContext.class);
        assertNotNull(metodo.getAnnotation(POST.class));
        assertEquals("planificar", metodo.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMINISTRADOR", "PRODUCCION"),
                Set.of(metodo.getAnnotation(RolesAllowed.class).value()));
        ProduccionResource recurso = new ProduccionResource(new ProduccionService() {
            @Override public long crearYGenerarPlan(long idUsuario, String observacion,
                    List<Long> idPedidos) {
                assertEquals(7, idUsuario);
                assertEquals(List.of(2L, 3L), idPedidos);
                return 44;
            }
        });
        try (Response respuesta = recurso.planificar(new PlanificarProduccionDTO(
                LocalDate.of(2026, 11, 1), List.of(2L, 3L), null), uri(),
                SesionDePrueba.usuario(7, Roles.PRODUCCION))) {
            assertEquals(201, respuesta.getStatus());
            assertEquals(44, ((gt.edu.umg.sancarlos.dto.ProduccionCreadaDTO)
                    respuesta.getEntity()).getIdProduccion());
        }
    }

    @Test void planificarRechazaListaVaciaRepetidaOLarga() {
        ProduccionResource recurso = new ProduccionResource(new ProduccionService());
        for (List<Long> ids : List.of(List.<Long>of(), List.of(2L, 2L),
                java.util.Collections.nCopies(101, 2L))) {
            assertThrows(BadRequestException.class,
                    () -> recurso.planificar(new PlanificarProduccionDTO(
                            LocalDate.of(2026, 11, 1), ids, null), uri(), null));
        }
    }

    @Test void listaDisponiblesExigeFechaYAccesoDeProduccion() throws Exception {
        var metodo = ProduccionResource.class.getMethod("pedidosDisponibles", String.class);
        assertNotNull(metodo.getAnnotation(GET.class));
        assertEquals("pedidos-disponibles", metodo.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMINISTRADOR", "PRODUCCION"),
                Set.of(metodo.getAnnotation(RolesAllowed.class).value()));
        ProduccionResource recurso = new ProduccionResource(new ProduccionService() {
            @Override public List<gt.edu.umg.sancarlos.dto.PedidoResumenDTO> pedidosDisponibles(LocalDate fecha) {
                assertEquals(LocalDate.of(2026, 11, 1), fecha);
                return List.of();
            }
        });
        assertEquals(200, recurso.pedidosDisponibles("2026-11-01").getStatus());
        assertThrows(BadRequestException.class, () -> recurso.pedidosDisponibles("mal"));
    }

    @Test void edicionYRecepcionDevuelven200() throws Exception {
        var edicion = PedidoResource.class.getMethod("editarPedido", String.class,
                EditarPedidoDTO.class, jakarta.ws.rs.core.SecurityContext.class);
        var recepcion = PedidoResource.class.getMethod("recibirPedido", String.class,
                RecepcionPedidoDTO.class, jakarta.ws.rs.core.SecurityContext.class);
        assertNotNull(edicion.getAnnotation(PUT.class));
        assertNotNull(recepcion.getAnnotation(POST.class));
        assertEquals(Set.of("ADMINISTRADOR", "SUCURSAL"),
                Set.of(edicion.getAnnotation(RolesAllowed.class).value()));
        assertEquals(Set.of("ADMINISTRADOR", "SUCURSAL"),
                Set.of(recepcion.getAnnotation(RolesAllowed.class).value()));
        PedidoResource recurso = new PedidoResource(new PedidoService() {
            @Override public Pedido editarPedido(long id, EditarPedidoDTO solicitud, UsuarioSesion usuario) {
                assertEquals(9, id); assertEquals(7, usuario.getIdUsuario()); return new Pedido();
            }
        }, new DespachoService() {
            @Override public Despacho recibirPedido(long id, UsuarioSesion usuario, String observacion) {
                assertEquals(9, id); assertEquals("faltante", observacion); return new Despacho();
            }
        });
        var sesion = SesionDePrueba.usuario(7, Roles.SUCURSAL);
        assertEquals(200, recurso.editarPedido("9", new EditarPedidoDTO(
                LocalDate.of(2026, 11, 1), null, List.of()), sesion).getStatus());
        assertEquals(200, recurso.recibirPedido("9", new RecepcionPedidoDTO("faltante"),
                sesion).getStatus());
    }

    @Test void conflictoDeOracleSeTraduceA409() {
        ProduccionResource recurso = new ProduccionResource(new ProduccionService() {
            @Override public long crearYGenerarPlan(long usuario, String observacion,
                    List<Long> pedidos) {
                throw new ProduccionConflictoException("Pedido ya asociado", null);
            }
        });
        RuntimeException causa = assertThrows(ProduccionConflictoException.class,
                () -> recurso.planificar(new PlanificarProduccionDTO(LocalDate.of(2026, 11, 1),
                        List.of(1L), null), uri(), SesionDePrueba.usuario(7, Roles.PRODUCCION)));
        assertEquals(409, new ApiExceptionMapper().toResponse(causa).getStatus());
    }

    private static UriInfo uri() {
        return (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(),
                new Class<?>[]{UriInfo.class}, (proxy, method, args) ->
                    "getBaseUriBuilder".equals(method.getName())
                            ? UriBuilder.fromUri("http://localhost/api/") : null);
    }
}
