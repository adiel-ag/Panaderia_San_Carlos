package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SesionFilterTest {

    private static final UsuarioSesion BODEGA = new UsuarioSesion(5, "bodega", "Bodeguero", "BODEGA", null);

    @Test
    void sinSesionNoIdentificaANadieNiConsultaOracle() {
        List<Long> consultas = new ArrayList<>();
        PeticionFalsa peticion = new PeticionFalsa();
        Contexto contexto = new Contexto();

        new SesionFilter(servicio(consultas, Optional.of(BODEGA)), peticion.request, null).filter(contexto.proxy);

        assertNull(contexto.seguridad);
        assertTrue(consultas.isEmpty());
        assertTrue(peticion.sesiones.isEmpty(), "el filtro no debe crear sesiones");
    }

    @Test
    void sesionVigenteEstableceElUsuarioYSuRol() {
        List<Long> consultas = new ArrayList<>();
        PeticionFalsa peticion = new PeticionFalsa();
        AuthResourceTest.sesionCon(peticion, 5L);
        Contexto contexto = new Contexto();

        new SesionFilter(servicio(consultas, Optional.of(BODEGA)), peticion.request, null).filter(contexto.proxy);

        assertEquals(List.of(5L), consultas, "revalida contra Oracle en cada peticion");
        UsuarioSecurityContext seguridad = assertInstanceOf(UsuarioSecurityContext.class, contexto.seguridad);
        assertSame(BODEGA, seguridad.getUserPrincipal());
        assertTrue(seguridad.isUserInRole("BODEGA"));
        assertFalse(seguridad.isUserInRole("ADMINISTRADOR"));
    }

    @Test
    void sesionQueYaNoEsVigenteSeCierraYQuedaAnonima() {
        PeticionFalsa peticion = new PeticionFalsa();
        PeticionFalsa.SesionFalsa sesion = AuthResourceTest.sesionCon(peticion, 5L);
        Contexto contexto = new Contexto();

        new SesionFilter(servicio(new ArrayList<>(), Optional.empty()), peticion.request, null).filter(contexto.proxy);

        assertTrue(sesion.invalidada, "usuario o rol desactivado, o clave cambiada");
        assertNull(contexto.seguridad);
    }

    @Test
    void siOracleFallaNoCortaLaPeticionYElLogoutSigueFuncionando() {
        DataAccessException caida = new DataAccessException("No fue posible validar la sesión",
                new SQLException("ORA-12541: no listener"));
        AutenticacionService sinOracle = new AutenticacionService() {
            @Override
            public Optional<UsuarioSesion> obtenerSesionVigente(long idUsuario, LocalDateTime inicio) {
                throw caida;
            }
        };
        PeticionFalsa peticion = new PeticionFalsa();
        PeticionFalsa.SesionFalsa sesion = AuthResourceTest.sesionCon(peticion, 5L);
        Contexto contexto = new Contexto();

        new SesionFilter(sinOracle, peticion.request, null).filter(contexto.proxy);

        SesionNoVerificada noVerificada = assertInstanceOf(SesionNoVerificada.class, contexto.seguridad);
        assertNull(noVerificada.getUserPrincipal(), "no da acceso a nadie");
        assertFalse(sesion.invalidada, "una caida de Oracle no cierra la sesion");
        assertSame(caida, assertThrows(DataAccessException.class,
                () -> SesionNoVerificada.exigirVerificada(noVerificada)));

        try (Response response = new AuthResource(sinOracle).cerrarSesion(peticion.request)) {
            assertEquals(200, response.getStatus());
        }
        assertTrue(sesion.invalidada, "el logout cierra la sesion sin consultar Oracle");
    }

    @Test
    void loginYLogoutNoConsultanOracleYMeSi() throws Exception {
        // Registra cada consulta a Oracle: con login y logout debe quedar
        // vacio, porque una base colgada no puede retrasar el cierre de sesion.
        List<Long> consultas = new ArrayList<>();
        AutenticacionService oracleColgado = new AutenticacionService() {
            @Override
            public Optional<UsuarioSesion> obtenerSesionVigente(long idUsuario, LocalDateTime inicio) {
                consultas.add(idUsuario);
                return Optional.of(BODEGA);
            }
        };
        Method logout = AuthResource.class.getMethod("cerrarSesion", jakarta.servlet.http.HttpServletRequest.class);
        Method login = AuthResource.class.getMethod("iniciarSesion",
                gt.edu.umg.sancarlos.dto.LoginDTO.class, jakarta.servlet.http.HttpServletRequest.class);
        Method me = AuthResource.class.getMethod("usuarioActual", SecurityContext.class);

        for (Method sinConsulta : new Method[]{logout, login}) {
            PeticionFalsa peticion = new PeticionFalsa();
            AuthResourceTest.sesionCon(peticion, 5L);
            Contexto contexto = new Contexto();
            new SesionFilter(oracleColgado, peticion.request, recurso(sinConsulta)).filter(contexto.proxy);
            assertNull(contexto.seguridad, sinConsulta.getName());
        }
        assertTrue(consultas.isEmpty(), "login y logout no deben tocar Oracle");

        PeticionFalsa peticion = new PeticionFalsa();
        AuthResourceTest.sesionCon(peticion, 5L);
        Contexto contexto = new Contexto();
        new SesionFilter(oracleColgado, peticion.request, recurso(me)).filter(contexto.proxy);
        assertEquals(List.of(5L), consultas, "/me si revalida la sesion");
        assertInstanceOf(UsuarioSecurityContext.class, contexto.seguridad);
    }

    private static ResourceInfo recurso(Method metodo) {
        return (ResourceInfo) Proxy.newProxyInstance(ResourceInfo.class.getClassLoader(),
                new Class<?>[]{ResourceInfo.class},
                (p, m, a) -> m.getName().equals("getResourceMethod") ? metodo : AuthResource.class);
    }

    private static AutenticacionService servicio(List<Long> consultas, Optional<UsuarioSesion> resultado) {
        return new AutenticacionService() {
            @Override
            public Optional<UsuarioSesion> obtenerSesionVigente(long idUsuario, LocalDateTime inicio) {
                consultas.add(idUsuario);
                return resultado;
            }
        };
    }

    /** ContainerRequestContext que solo recuerda el SecurityContext asignado. */
    private static final class Contexto {

        SecurityContext seguridad;

        final ContainerRequestContext proxy = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(), new Class<?>[]{ContainerRequestContext.class},
                (p, method, args) -> {
                    if (method.getName().equals("setSecurityContext")) {
                        seguridad = (SecurityContext) args[0];
                    }
                    return null;
                });
    }
}
