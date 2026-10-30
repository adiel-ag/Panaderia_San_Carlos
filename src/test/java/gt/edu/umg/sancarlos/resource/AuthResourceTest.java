package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.LoginDTO;
import gt.edu.umg.sancarlos.dto.CambiarClaveDTO;
import gt.edu.umg.sancarlos.dto.MensajeDTO;
import gt.edu.umg.sancarlos.exception.CredencialesInvalidasException;
import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import gt.edu.umg.sancarlos.service.AutenticacionService.SesionIniciada;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthResourceTest {

    private static final UsuarioSesion ADMIN =
            new UsuarioSesion(1, "admin", "Administrador", "ADMINISTRADOR", null);
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 30, 8, 0);

    @Test
    void exponeLoginLogoutYMeSinExigirSesion() throws Exception {
        assertEquals("/auth", AuthResource.class.getAnnotation(Path.class).value());
        Method login = AuthResource.class.getMethod("iniciarSesion", LoginDTO.class, HttpServletRequest.class);
        Method logout = AuthResource.class.getMethod("cerrarSesion", HttpServletRequest.class);
        Method me = AuthResource.class.getMethod("usuarioActual", SecurityContext.class);
        assertNotNull(login.getAnnotation(POST.class));
        assertEquals("login", login.getAnnotation(Path.class).value());
        assertNotNull(logout.getAnnotation(POST.class));
        assertEquals("logout", logout.getAnnotation(Path.class).value());
        assertNotNull(me.getAnnotation(GET.class));
        assertEquals("me", me.getAnnotation(Path.class).value());
        for (Method metodo : new Method[]{login, logout, me}) {
            assertNotNull(metodo.getAnnotation(PermitAll.class), metodo.getName());
        }
    }

    @Test
    void loginCorrectoCreaSesionNuevaYDescartaLaAnterior() {
        char[][] claveRecibida = new char[1][];
        AutenticacionService service = new AutenticacionService() {
            @Override
            public SesionIniciada iniciarSesion(String username, char[] clave) {
                assertEquals("admin", username);
                claveRecibida[0] = clave;
                assertArrayEquals("secreta1".toCharArray(), clave);
                return new SesionIniciada(ADMIN, INICIO);
            }
        };
        PeticionFalsa peticion = new PeticionFalsa();
        PeticionFalsa.SesionFalsa anterior = sesionCon(peticion, 99L);

        try (Response response = new AuthResource(service).iniciarSesion(login("admin", "secreta1"),
                peticion.request)) {
            assertEquals(200, response.getStatus());
            assertSame(ADMIN, response.getEntity());
        }

        assertTrue(anterior.invalidada, "la sesion previa se descarta (fijacion de sesion)");
        PeticionFalsa.SesionFalsa nueva = peticion.sesionActual();
        assertNotSame(anterior, nueva);
        assertEquals(1L, nueva.atributos.get(SesionUsuario.ATRIBUTO_ID_USUARIO));
        assertEquals(INICIO, nueva.atributos.get(SesionUsuario.ATRIBUTO_INICIO));
        assertArrayEquals(new char[8], claveRecibida[0], "la clave se borra de memoria al terminar");
    }

    @Test
    void cambiarClaveRenuevaSesionConFechaDeCambio() {
        LocalDateTime cambio = INICIO.plusMinutes(2);
        AutenticacionService service = new AutenticacionService() {
            @Override public SesionIniciada cambiarClave(UsuarioSesion usuario,
                    char[] actual, char[] nueva) {
                assertSame(ADMIN, usuario);
                assertArrayEquals("anterior-1".toCharArray(), actual);
                assertArrayEquals("nueva-clave-1".toCharArray(), nueva);
                return new SesionIniciada(ADMIN, cambio);
            }
        };
        PeticionFalsa peticion = new PeticionFalsa();
        PeticionFalsa.SesionFalsa anterior = sesionCon(peticion, 1L);

        try (Response respuesta = new AuthResource(service).cambiarClave(
                new CambiarClaveDTO("anterior-1", "nueva-clave-1"),
                new UsuarioSecurityContext(ADMIN, false), peticion.request)) {
            assertEquals(200, respuesta.getStatus());
            assertEquals("Contraseña actualizada", ((MensajeDTO) respuesta.getEntity()).getMensaje());
        }
        assertTrue(anterior.invalidada);
        assertNotSame(anterior, peticion.sesionActual());
        assertEquals(1L, peticion.sesionActual().atributos.get(SesionUsuario.ATRIBUTO_ID_USUARIO));
        assertEquals(cambio, peticion.sesionActual().atributos.get(SesionUsuario.ATRIBUTO_INICIO));
    }

    @Test
    void loginFallidoNoCreaSesion() {
        AutenticacionService service = new AutenticacionService() {
            @Override
            public SesionIniciada iniciarSesion(String username, char[] clave) {
                throw new CredencialesInvalidasException("Usuario o contraseña incorrectos.", null);
            }
        };
        PeticionFalsa peticion = new PeticionFalsa();

        assertThrows(CredencialesInvalidasException.class,
                () -> new AuthResource(service).iniciarSesion(login("admin", "mala"), peticion.request));
        assertTrue(peticion.sesiones.isEmpty());
        assertThrows(BadRequestException.class,
                () -> new AuthResource(service).iniciarSesion(null, peticion.request));
    }

    @Test
    void logoutInvalidaLaSesionYEsIdempotente() {
        PeticionFalsa peticion = new PeticionFalsa();
        PeticionFalsa.SesionFalsa sesion = sesionCon(peticion, 1L);
        AuthResource resource = new AuthResource(new AutenticacionService());

        try (Response response = resource.cerrarSesion(peticion.request)) {
            assertEquals(200, response.getStatus());
            assertEquals("Sesión cerrada", ((MensajeDTO) response.getEntity()).getMensaje());
        }
        assertTrue(sesion.invalidada);
        assertNull(peticion.sesionActual());
        try (Response response = resource.cerrarSesion(peticion.request)) {
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void meDevuelveElUsuarioDeLaSesionO401() {
        AuthResource resource = new AuthResource(new AutenticacionService());

        try (Response response = resource.usuarioActual(new UsuarioSecurityContext(ADMIN, false))) {
            assertEquals(200, response.getStatus());
            assertSame(ADMIN, response.getEntity());
        }
        assertThrows(NoAutenticadoException.class, () -> resource.usuarioActual(null));
    }

    @Test
    void elJsonDelUsuarioNoIncluyeDatosSobrantes() throws Exception {
        String json = new JacksonObjectMapperProvider().getContext(UsuarioSesion.class)
                .writeValueAsString(new UsuarioSesion(3, "chiva", "Sucursal Chiva", "SUCURSAL", 2L));

        assertEquals("{\"idUsuario\":3,\"username\":\"chiva\",\"nombre\":\"Sucursal Chiva\","
                + "\"rol\":\"SUCURSAL\",\"idSucursal\":2}", json);
    }

    static PeticionFalsa.SesionFalsa sesionCon(PeticionFalsa peticion, long idUsuario) {
        peticion.request.getSession(true);
        PeticionFalsa.SesionFalsa sesion = peticion.sesionActual();
        sesion.atributos.put(SesionUsuario.ATRIBUTO_ID_USUARIO, idUsuario);
        sesion.atributos.put(SesionUsuario.ATRIBUTO_INICIO, INICIO);
        return sesion;
    }

    private static LoginDTO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }
}
