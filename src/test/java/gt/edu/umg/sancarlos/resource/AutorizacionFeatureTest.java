package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.dto.AsignarClaveDTO;
import gt.edu.umg.sancarlos.exception.AccesoDenegadoException;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutorizacionFeatureTest {

    @Test
    void filtroDistingueSinSesionDeRolSinPermiso() {
        AutorizacionFeature.FiltroRoles soloAdmin = new AutorizacionFeature.FiltroRoles(Set.of("ADMINISTRADOR"));

        assertThrows(NoAutenticadoException.class, () -> soloAdmin.filter(contexto(null)));
        assertThrows(AccesoDenegadoException.class, () -> soloAdmin.filter(contexto(usuario("BODEGA"))));
        assertDoesNotThrow(() -> soloAdmin.filter(contexto(usuario("ADMINISTRADOR"))));
        assertThrows(AccesoDenegadoException.class,
                () -> new AutorizacionFeature.FiltroRoles(null).filter(contexto(usuario("ADMINISTRADOR"))),
                "@DenyAll: nadie pasa");
    }

    @Test
    void sesionNoVerificablePorOracleRespondeErrorDeBaseYNo401() {
        DataAccessException caida = new DataAccessException("No fue posible validar la sesión", null);
        SesionNoVerificada noVerificada = new SesionNoVerificada(caida, false);
        ContainerRequestContext contexto = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (p, m, a) -> m.getName().equals("getSecurityContext") ? noVerificada : null);

        assertSame(caida, assertThrows(DataAccessException.class,
                () -> new AutorizacionFeature.FiltroRoles(Set.of("ADMINISTRADOR")).filter(contexto)));
        assertSame(caida, assertThrows(DataAccessException.class,
                () -> new AuthResource(new AutenticacionService()).usuarioActual(noVerificada)));
        try (jakarta.ws.rs.core.Response response = new ApiExceptionMapper().toResponse(caida)) {
            assertEquals(500, response.getStatus());
        }
    }

    @Test
    void soloRegistraFiltroDondeHayRolesDeclarados() throws Exception {
        assertEquals(1, registrados(UsuarioResource.class.getMethod("asignarClave", String.class,
                AsignarClaveDTO.class), UsuarioResource.class).size(), "@RolesAllowed");
        assertTrue(registrados(AuthResource.class.getMethod("iniciarSesion",
                gt.edu.umg.sancarlos.dto.LoginDTO.class, HttpServletRequest.class), AuthResource.class)
                .isEmpty(), "@PermitAll");
        assertTrue(registrados(ProductoResource.class.getMethod("listarProductos"), ProductoResource.class)
                .isEmpty(), "sin anotacion: la parte 3 decidira");
    }

    @Test
    void jerseyResponde401403Y200SegunLaSesion() {
        List<char[]> claves = new ArrayList<>();
        AutenticacionService service = new AutenticacionService() {
            @Override
            public void establecerClave(long idUsuario, char[] clave) {
                claves.add(clave.clone());
            }
        };

        ContainerResponse sinSesion = asignarClave(service, null);
        assertEquals(401, sinSesion.getStatus());
        assertEquals("Debe iniciar sesión.", assertInstanceOf(ApiError.class, sinSesion.getEntity()).getMessage());

        ContainerResponse bodega = asignarClave(service, usuario("BODEGA"));
        assertEquals(403, bodega.getStatus());

        ContainerResponse admin = asignarClave(service, usuario("ADMINISTRADOR"));
        assertEquals(200, admin.getStatus());
        assertEquals(1, claves.size(), "solo el administrador llego al servicio");
        assertArrayEquals("clave-nueva-1".toCharArray(), claves.get(0));
    }

    private static ContainerResponse asignarClave(AutenticacionService service, UsuarioSesion usuario) {
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig()
                .register(new UsuarioResource(service))
                .register(new SesionDePrueba(usuario))
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
        URI base = URI.create("http://localhost/api/");
        ContainerRequest request = new ContainerRequest(base, base.resolve("usuarios/7/clave"), "PUT",
                null, new MapPropertiesDelegate(), null);
        request.getHeaders().putSingle("Content-Type", "application/json");
        request.setEntityStream(new ByteArrayInputStream(
                "{\"password\":\"clave-nueva-1\"}".getBytes(StandardCharsets.UTF_8)));
        try {
            return handler.apply(request).get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /** Hace de SesionFilter: identifica al usuario dado, o a nadie. */
    @Priority(Priorities.AUTHENTICATION)
    private static final class SesionDePrueba implements ContainerRequestFilter {

        private final UsuarioSesion usuario;

        SesionDePrueba(UsuarioSesion usuario) {
            this.usuario = usuario;
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            if (usuario != null) {
                requestContext.setSecurityContext(new UsuarioSecurityContext(usuario, false));
            }
        }
    }

    private static List<Object> registrados(java.lang.reflect.Method metodo, Class<?> clase) {
        List<Object> registrados = new ArrayList<>();
        jakarta.ws.rs.container.ResourceInfo info = (jakarta.ws.rs.container.ResourceInfo) Proxy.newProxyInstance(
                AutorizacionFeatureTest.class.getClassLoader(),
                new Class<?>[]{jakarta.ws.rs.container.ResourceInfo.class},
                (p, m, a) -> m.getName().equals("getResourceMethod") ? metodo : clase);
        jakarta.ws.rs.core.FeatureContext contexto = (jakarta.ws.rs.core.FeatureContext) Proxy.newProxyInstance(
                AutorizacionFeatureTest.class.getClassLoader(),
                new Class<?>[]{jakarta.ws.rs.core.FeatureContext.class},
                (p, m, a) -> {
                    if (m.getName().equals("register")) {
                        registrados.add(a[0]);
                    }
                    return p;
                });
        new AutorizacionFeature().configure(info, contexto);
        return registrados;
    }

    private static UsuarioSesion usuario(String rol) {
        return new UsuarioSesion(1, "u", "Usuario", rol, null);
    }

    private static ContainerRequestContext contexto(UsuarioSesion usuario) {
        SecurityContext seguridad = usuario == null ? null : new UsuarioSecurityContext(usuario, false);
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (p, m, a) -> m.getName().equals("getSecurityContext") ? seguridad : null);
    }
}
