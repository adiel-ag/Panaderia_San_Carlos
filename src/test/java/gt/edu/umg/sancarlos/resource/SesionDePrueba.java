package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.UsuarioSesion;
import jakarta.ws.rs.core.SecurityContext;

/** SecurityContext de un usuario autenticado, para llamar recursos sin Tomcat. */
final class SesionDePrueba {

    private SesionDePrueba() {
    }

    static SecurityContext usuario(long idUsuario, String rol) {
        return new UsuarioSecurityContext(
                new UsuarioSesion(idUsuario, "u" + idUsuario, "Usuario " + idUsuario, rol, null), false);
    }
}
