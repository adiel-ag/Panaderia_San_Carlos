package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.exception.AccesoDenegadoException;
import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Aplica @RolesAllowed, @PermitAll y @DenyAll de cada endpoint. A diferencia
 * del filtro estandar de Jersey, distingue sin sesion (401) de rol sin
 * permiso (403). Se ejecuta despues de SesionFilter.
 *
 * Seguro por defecto: un endpoint sin ninguna de esas anotaciones exige una
 * sesion iniciada (cualquier rol). Solo los marcados con @PermitAll son
 * publicos, de modo que un endpoint nuevo nunca queda abierto por olvido.
 */
@Provider
public class AutorizacionFeature implements DynamicFeature {

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        Method metodo = resourceInfo.getResourceMethod();
        Class<?> clase = resourceInfo.getResourceClass();
        if (metodo == null || clase == null) {
            return;
        }
        if (metodo.isAnnotationPresent(DenyAll.class)) {
            context.register(new FiltroRoles(null));
            return;
        }
        if (metodo.isAnnotationPresent(PermitAll.class)) {
            return;
        }
        RolesAllowed roles = metodo.getAnnotation(RolesAllowed.class);
        if (roles == null) {
            if (clase.isAnnotationPresent(DenyAll.class)) {
                context.register(new FiltroRoles(null));
                return;
            }
            if (clase.isAnnotationPresent(PermitAll.class)) {
                return;
            }
            roles = clase.getAnnotation(RolesAllowed.class);
        }
        if (roles != null) {
            context.register(new FiltroRoles(Set.of(roles.value())));
        } else {
            context.register(FiltroRoles.soloSesion());
        }
    }

    /** rolesPermitidos nulo y sin cualquierRol = @DenyAll: nadie pasa. */
    @Priority(Priorities.AUTHORIZATION)
    static final class FiltroRoles implements ContainerRequestFilter {

        private final Set<String> rolesPermitidos;
        private final boolean cualquierRol;

        FiltroRoles(Set<String> rolesPermitidos) {
            this(rolesPermitidos, false);
        }

        private FiltroRoles(Set<String> rolesPermitidos, boolean cualquierRol) {
            this.rolesPermitidos = rolesPermitidos;
            this.cualquierRol = cualquierRol;
        }

        /** Endpoint sin anotaciones: basta con tener sesion. */
        static FiltroRoles soloSesion() {
            return new FiltroRoles(null, true);
        }

        @Override
        public void filter(ContainerRequestContext requestContext) {
            SecurityContext seguridad = requestContext.getSecurityContext();
            SesionNoVerificada.exigirVerificada(seguridad);
            if (seguridad == null || !(seguridad.getUserPrincipal() instanceof UsuarioSesion)) {
                throw new NoAutenticadoException("Debe iniciar sesión.", null);
            }
            if (cualquierRol) {
                return;
            }
            if (rolesPermitidos == null
                    || rolesPermitidos.stream().noneMatch(seguridad::isUserInRole)) {
                throw new AccesoDenegadoException(
                        "No tiene permiso para realizar esta operación.", null);
            }
        }
    }
}
