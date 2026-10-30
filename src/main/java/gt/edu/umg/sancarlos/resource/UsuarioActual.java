package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import jakarta.ws.rs.core.SecurityContext;

/**
 * El usuario que realiza una operacion sale siempre de la sesion, nunca del
 * JSON: asi nadie puede registrar pedidos, producciones, movimientos o
 * despachos a nombre de otro.
 */
final class UsuarioActual {

    private UsuarioActual() {
    }

    static UsuarioSesion de(SecurityContext seguridad) {
        SesionNoVerificada.exigirVerificada(seguridad);
        if (seguridad == null || !(seguridad.getUserPrincipal() instanceof UsuarioSesion usuario)) {
            throw new NoAutenticadoException("Debe iniciar sesión.", null);
        }
        return usuario;
    }

    static long id(SecurityContext seguridad) {
        return de(seguridad).getIdUsuario();
    }
}
