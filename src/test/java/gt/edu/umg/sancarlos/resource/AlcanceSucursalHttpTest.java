package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.DespachoService;
import gt.edu.umg.sancarlos.service.PedidoService;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.net.URI;
import java.util.Optional;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlcanceSucursalHttpTest {

    private static final URI BASE = URI.create("http://localhost/api/");
    private static final ObjectMapper JSON = new JacksonObjectMapperProvider().getContext(Pedido.class);

    @Test
    void pedidoAjenoRespondeIgualQueInexistente() throws Exception {
        ContainerResponse ajeno = get(aplicacion(Roles.SUCURSAL, 2L, true), "pedidos/1");
        ContainerResponse inexistente = get(aplicacion(Roles.SUCURSAL, 2L, false), "pedidos/1");
        assertEquals(404, ajeno.getStatus());
        assertEquals(404, inexistente.getStatus());
        assertEquals(JSON.writeValueAsString(inexistente.getEntity()),
                JSON.writeValueAsString(ajeno.getEntity()));
    }

    @Test
    void sucursalPropiaYGerenteVenPedidoYDespacho() throws Exception {
        for (String rol : new String[]{Roles.SUCURSAL, Roles.GERENTE}) {
            ApplicationHandler handler = aplicacion(rol, rol.equals(Roles.SUCURSAL) ? 1L : null, true);
            assertEquals(200, get(handler, "pedidos/1").getStatus(), rol);
            assertEquals(200, get(handler, "pedidos/1/despacho").getStatus(), rol);
        }
    }

    @Test
    void sucursalAjenaNoVeDespacho() throws Exception {
        ContainerResponse ajeno = get(aplicacion(Roles.SUCURSAL, 2L, true),
                "pedidos/1/despacho");
        ContainerResponse inexistente = get(aplicacion(Roles.SUCURSAL, 2L, false),
                "pedidos/1/despacho");
        assertEquals(404, ajeno.getStatus());
        assertEquals(404, inexistente.getStatus());
        assertEquals(JSON.writeValueAsString(inexistente.getEntity()),
                JSON.writeValueAsString(ajeno.getEntity()));
    }

    private static ApplicationHandler aplicacion(String rol, Long idSucursal, boolean existe) {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(1);
        pedido.setIdSucursal(1);
        Despacho despacho = new Despacho();
        despacho.setIdPedido(1);
        despacho.setIdSucursal(1L);
        return new ApplicationHandler(new ResourceConfig()
                .register(new PedidoResource(new PedidoService() {
                    @Override
                    public Optional<Pedido> buscarPedidoPorId(long idPedido) {
                        return existe && idPedido == 1 ? Optional.of(pedido) : Optional.empty();
                    }
                }))
                .register(new DespachoResource(new DespachoService() {
                    @Override
                    public Optional<Despacho> buscarPorPedido(long idPedido) {
                        return existe && idPedido == 1 ? Optional.of(despacho) : Optional.empty();
                    }
                }))
                .register(new SesionConSucursal(rol, idSucursal))
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
    }

    private static ContainerResponse get(ApplicationHandler handler, String ruta) throws Exception {
        ContainerRequest request = new ContainerRequest(BASE, BASE.resolve(ruta), "GET", null,
                new MapPropertiesDelegate(), null);
        return handler.apply(request).get();
    }

    @Priority(Priorities.AUTHENTICATION)
    private static final class SesionConSucursal implements ContainerRequestFilter {

        private final String rol;
        private final Long idSucursal;

        SesionConSucursal(String rol, Long idSucursal) {
            this.rol = rol;
            this.idSucursal = idSucursal;
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            requestContext.setSecurityContext(new UsuarioSecurityContext(
                    new UsuarioSesion(7, "u", "Usuario", rol, idSucursal), false));
        }
    }
}
