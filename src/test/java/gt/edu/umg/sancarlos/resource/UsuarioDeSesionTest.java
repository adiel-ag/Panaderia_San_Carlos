package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.ExistenciaDTO;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.DespachoService;
import gt.edu.umg.sancarlos.service.InventarioService;
import gt.edu.umg.sancarlos.service.PedidoService;
import gt.edu.umg.sancarlos.service.ProduccionService;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El usuario de cada operacion sale de la sesion: aunque el JSON diga
 * "idUsuario": 999, el servicio recibe el ID del usuario que inicio sesion,
 * y el campo sobrante no provoca un 400.
 */
class UsuarioDeSesionTest {

    private static final long USUARIO_SESION = 7;
    private static final String CON_USUARIO_FALSO = "\"idUsuario\":999";

    private final List<Long> usuariosRecibidos = new ArrayList<>();

    @Test
    void crearPedidoUsaElUsuarioDeLaSesion() {
        assertEquals(201, llamar("POST", "pedidos", "{" + CON_USUARIO_FALSO
                + ",\"idSucursal\":1,\"fechaEntrega\":\"2026-11-01\",\"detalles\":[]}"));
        assertEquals(List.of(USUARIO_SESION), usuariosRecibidos);
    }

    @Test
    void crearYFinalizarProduccionUsanElUsuarioDeLaSesion() {
        assertEquals(201, llamar("POST", "producciones", "{" + CON_USUARIO_FALSO + ",\"observacion\":\"x\"}"));
        assertEquals(200, llamar("POST", "producciones/4/finalizar", "{" + CON_USUARIO_FALSO + "}"));
        assertEquals(200, llamar("POST", "producciones/4/finalizar", null), "finalizar sin cuerpo");
        assertEquals(List.of(USUARIO_SESION, USUARIO_SESION, USUARIO_SESION), usuariosRecibidos);
    }

    @Test
    void entradasYSalidasDeInventarioUsanElUsuarioDeLaSesion() {
        String cuerpo = "{" + CON_USUARIO_FALSO + ",\"cantidad\":1}";
        assertEquals(200, llamar("POST", "inventario/productos/33/entradas", cuerpo));
        assertEquals(200, llamar("POST", "inventario/productos/33/salidas", cuerpo));
        assertEquals(List.of(USUARIO_SESION, USUARIO_SESION), usuariosRecibidos);
    }

    @Test
    void despacharUsaElUsuarioDeLaSesionConOSinCuerpo() {
        assertEquals(201, llamar("POST", "pedidos/2/despacho", "{" + CON_USUARIO_FALSO + "}"));
        assertEquals(201, llamar("POST", "pedidos/2/despacho", null));
        assertEquals(List.of(USUARIO_SESION, USUARIO_SESION), usuariosRecibidos);
    }

    private int llamar(String metodo, String ruta, String json) {
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig()
                .register(new PedidoResource(new PedidoService() {
                    @Override
                    public long crearPedido(CrearPedidoDTO solicitud) {
                        usuariosRecibidos.add(solicitud.getIdUsuario());
                        return 501;
                    }
                }))
                .register(new ProduccionResource(new ProduccionService() {
                    @Override
                    public long crearProduccion(long idUsuario, String observacion) {
                        usuariosRecibidos.add(idUsuario);
                        return 90;
                    }

                    @Override
                    public void finalizarProduccion(long idProduccion, long idUsuario) {
                        usuariosRecibidos.add(idUsuario);
                    }
                }))
                .register(new InventarioResource(new InventarioService() {
                    @Override
                    public ExistenciaDTO registrarEntrada(long idProducto, long idUsuario,
                            BigDecimal cantidad, String observacion) {
                        usuariosRecibidos.add(idUsuario);
                        return new ExistenciaDTO(idProducto, BigDecimal.ONE);
                    }

                    @Override
                    public ExistenciaDTO registrarSalida(long idProducto, long idUsuario,
                            BigDecimal cantidad, String observacion) {
                        usuariosRecibidos.add(idUsuario);
                        return new ExistenciaDTO(idProducto, BigDecimal.ZERO);
                    }
                }))
                .register(new DespachoResource(new DespachoService() {
                    @Override
                    public Despacho despacharPedido(long idPedido, long idUsuario, String observacion) {
                        usuariosRecibidos.add(idUsuario);
                        return new Despacho();
                    }
                }))
                .register(new SesionDelUsuario7())
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
        URI base = URI.create("http://localhost/api/");
        ContainerRequest request = new ContainerRequest(base, base.resolve(ruta), metodo, null,
                new MapPropertiesDelegate(), null);
        if (json != null) {
            request.getHeaders().putSingle("Content-Type", "application/json");
            request.setEntityStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            return handler.apply(request).get().getStatus();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /** Sesion de un ADMINISTRADOR con ID 7, que puede usar todos estos endpoints. */
    @Priority(Priorities.AUTHENTICATION)
    private static final class SesionDelUsuario7 implements ContainerRequestFilter {

        @Override
        public void filter(ContainerRequestContext requestContext) {
            requestContext.setSecurityContext(new UsuarioSecurityContext(
                    new UsuarioSesion(USUARIO_SESION, "admin", "Administrador", "ADMINISTRADOR", null), false));
        }
    }
}
