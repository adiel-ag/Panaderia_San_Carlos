package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Identifica al usuario de la sesion en cada peticion. No rechaza a nadie:
 * solo establece quien es; AutorizacionFeature decide si puede pasar.
 * La sesion se revalida contra Oracle en cada peticion y se cierra si el
 * usuario o su rol se desactivaron o si su clave cambio despues de entrar.
 * Los endpoints marcados con @SinRevalidarSesion (login y logout) no
 * consultan Oracle: no necesitan saber quien es el usuario.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class SesionFilter implements ContainerRequestFilter {

    private final AutenticacionService autenticacionService;

    @Context
    private HttpServletRequest request;

    @Context
    private ResourceInfo resourceInfo;

    public SesionFilter() {
        this(new AutenticacionService(), null, null);
    }

    SesionFilter(AutenticacionService autenticacionService, HttpServletRequest request,
            ResourceInfo resourceInfo) {
        this.autenticacionService = autenticacionService;
        this.request = request;
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (request == null || sinRevalidar()) {
            return;
        }
        HttpSession sesion = request.getSession(false);
        if (sesion == null) {
            return;
        }
        Long idUsuario;
        LocalDateTime inicio;
        try {
            idUsuario = SesionUsuario.idUsuario(sesion);
            inicio = SesionUsuario.inicio(sesion);
        } catch (IllegalStateException sesionInvalidada) {
            return;
        }
        if (idUsuario == null) {
            return;
        }
        boolean seguro = requestContext.getSecurityContext() != null
                && requestContext.getSecurityContext().isSecure();
        Optional<UsuarioSesion> usuario;
        try {
            usuario = autenticacionService.obtenerSesionVigente(idUsuario, inicio);
        } catch (DataAccessException oracleNoDisponible) {
            // No se corta la peticion: una caida de Oracle no cierra la sesion
            // ni da acceso; quien necesite al usuario relanza el error (500).
            requestContext.setSecurityContext(new SesionNoVerificada(oracleNoDisponible, seguro));
            return;
        }
        if (usuario.isEmpty()) {
            SesionUsuario.cerrar(request);
            return;
        }
        requestContext.setSecurityContext(new UsuarioSecurityContext(usuario.get(), seguro));
    }

    private boolean sinRevalidar() {
        Method metodo = resourceInfo == null ? null : resourceInfo.getResourceMethod();
        return metodo != null && metodo.isAnnotationPresent(SinRevalidarSesion.class);
    }
}
