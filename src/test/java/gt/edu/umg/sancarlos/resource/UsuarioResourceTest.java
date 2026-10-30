package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.AsignarClaveDTO;
import gt.edu.umg.sancarlos.dto.MensajeDTO;
import gt.edu.umg.sancarlos.dto.CrearUsuarioDTO;
import gt.edu.umg.sancarlos.dto.UsuarioDTO;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import gt.edu.umg.sancarlos.service.UsuarioService;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UsuarioResourceTest {

    @Test
    void crearResponde201YLocationSinDatosDeClave() throws Exception {
        UsuarioDTO usuario = new UsuarioDTO(7, "Ana", "ana", "GERENTE", null, null,
                "ACTIVO", null, null, false, true);
        UsuarioService servicio = new UsuarioService() {
            @Override public UsuarioDTO crear(CrearUsuarioDTO solicitud, char[] clave) {
                assertArrayEquals("clave-nueva-1".toCharArray(), clave);
                return usuario;
            }
        };
        UsuarioResource recurso = new UsuarioResource(new AutenticacionService(), servicio);
        try (Response respuesta = recurso.crearUsuario(new CrearUsuarioDTO(
                "Ana", "ana", "GERENTE", null, "clave-nueva-1"))) {
            assertEquals(201, respuesta.getStatus());
            assertEquals("/usuarios/7", respuesta.getLocation().toString());
            assertInstanceOf(UsuarioDTO.class, respuesta.getEntity());
        }
        var json = new JacksonObjectMapperProvider().getContext(UsuarioDTO.class)
                .readTree(new JacksonObjectMapperProvider().getContext(UsuarioDTO.class)
                        .writeValueAsString(usuario));
        assertEquals(java.util.Set.of("idUsuario", "nombre", "username", "rol",
                "idSucursal", "nombreSucursal", "estado", "fechaCreacion",
                "ultimoAcceso", "bloqueado", "tieneClave"),
                java.util.stream.StreamSupport.stream(
                        java.util.Spliterators.spliteratorUnknownSize(json.fieldNames(), 0), false)
                        .collect(java.util.stream.Collectors.toSet()));
        for (String secreto : new String[]{"clave_hash", "salt", "algoritmo", "iteraciones", "password"}) {
            assertFalse(json.has(secreto));
        }
    }

    @Test
    void postUsuarioPorHttpDevuelve201YLocation() throws Exception {
        UsuarioService servicio = new UsuarioService() {
            @Override public UsuarioDTO crear(CrearUsuarioDTO solicitud, char[] clave) {
                return new UsuarioDTO(7, solicitud.nombre(), solicitud.username(),
                        solicitud.rol(), null, null, "ACTIVO", null, null, false, true);
            }
        };
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig()
                .register(new UsuarioResource(new AutenticacionService(), servicio))
                .register(new SesionAdmin())
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
        URI base = URI.create("http://localhost/api/");
        ContainerRequest request = new ContainerRequest(base, base.resolve("usuarios"), "POST",
                null, new MapPropertiesDelegate(), null);
        request.getHeaders().putSingle("Content-Type", "application/json");
        request.setEntityStream(new ByteArrayInputStream(
                "{\"nombre\":\"Ana\",\"username\":\"ana\",\"rol\":\"GERENTE\",\"password\":\"clave-nueva-1\"}"
                        .getBytes(StandardCharsets.UTF_8)));
        var respuesta = handler.apply(request).get();
        assertEquals(201, respuesta.getStatus());
        assertTrue(respuesta.getHeaderString("Location").endsWith("/usuarios/7"));
    }

    @Priority(Priorities.AUTHENTICATION)
    private static final class SesionAdmin implements ContainerRequestFilter {
        @Override public void filter(ContainerRequestContext contexto) {
            contexto.setSecurityContext(new UsuarioSecurityContext(new UsuarioSesion(
                    1, "admin", "Admin", "ADMINISTRADOR", null), false));
        }
    }

    @Test
    void asignarClaveEsSoloParaAdministrador() throws Exception {
        Method metodo = UsuarioResource.class.getMethod("asignarClave", String.class, AsignarClaveDTO.class);
        assertEquals("/usuarios", UsuarioResource.class.getAnnotation(Path.class).value());
        assertEquals("{id}/clave", metodo.getAnnotation(Path.class).value());
        assertNotNull(metodo.getAnnotation(PUT.class));
        assertArrayEquals(new String[]{"ADMINISTRADOR"}, metodo.getAnnotation(RolesAllowed.class).value());
    }

    @Test
    void asignaLaClaveYLaBorraDeMemoria() {
        List<char[]> recibidas = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        AutenticacionService service = new AutenticacionService() {
            @Override
            public void establecerClave(long idUsuario, char[] clave) {
                ids.add(idUsuario);
                recibidas.add(clave);
            }
        };

        try (Response response = new UsuarioResource(service).asignarClave("7", clave("clave-nueva-1"))) {
            assertEquals(200, response.getStatus());
            assertEquals("Contraseña asignada", ((MensajeDTO) response.getEntity()).getMensaje());
        }
        assertEquals(List.of(7L), ids);
        assertArrayEquals(new char[13], recibidas.get(0));
    }

    @Test
    void rechazaIdYCuerpoInvalidosSinLlamarAlServicio() {
        List<Long> ids = new ArrayList<>();
        AutenticacionService service = new AutenticacionService() {
            @Override
            public void establecerClave(long idUsuario, char[] clave) {
                ids.add(idUsuario);
            }
        };
        UsuarioResource resource = new UsuarioResource(service);

        assertThrows(BadRequestException.class, () -> resource.asignarClave("abc", clave("clave-nueva-1")));
        assertThrows(BadRequestException.class, () -> resource.asignarClave("0", clave("clave-nueva-1")));
        assertThrows(BadRequestException.class, () -> resource.asignarClave("7", null));
        assertThrows(BadRequestException.class, () -> resource.asignarClave("7", new AsignarClaveDTO()));
        assertTrue(ids.isEmpty());
    }

    private static AsignarClaveDTO clave(String valor) {
        AsignarClaveDTO dto = new AsignarClaveDTO();
        dto.setPassword(valor);
        return dto;
    }
}
