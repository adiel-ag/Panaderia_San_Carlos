package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.UsuarioSesion;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;

/** Expone el usuario de la sesion a Jersey y a la autorizacion por rol. */
class UsuarioSecurityContext implements SecurityContext {

    private final UsuarioSesion usuario;
    private final boolean seguro;

    UsuarioSecurityContext(UsuarioSesion usuario, boolean seguro) {
        this.usuario = usuario;
        this.seguro = seguro;
    }

    @Override
    public Principal getUserPrincipal() {
        return usuario;
    }

    @Override
    public boolean isUserInRole(String role) {
        return usuario.getRol() != null && usuario.getRol().equals(role);
    }

    @Override
    public boolean isSecure() {
        return seguro;
    }

    @Override
    public String getAuthenticationScheme() {
        return FORM_AUTH;
    }
}
