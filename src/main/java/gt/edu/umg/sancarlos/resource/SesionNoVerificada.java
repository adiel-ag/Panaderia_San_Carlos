package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import jakarta.ws.rs.core.SecurityContext;
import java.security.Principal;

/**
 * Hay una sesion, pero Oracle no respondio al revalidarla. No da acceso a
 * nadie; quien necesite al usuario relanza el error original (500) en vez de
 * responder 401, para no confundir una caida de la base con "sin sesion".
 * Las operaciones que no necesitan al usuario siguen su curso; el logout ni
 * siquiera llega aqui (ver @SinRevalidarSesion).
 */
class SesionNoVerificada implements SecurityContext {

    private final DataAccessException causa;
    private final boolean seguro;

    SesionNoVerificada(DataAccessException causa, boolean seguro) {
        this.causa = causa;
        this.seguro = seguro;
    }

    DataAccessException causa() {
        return causa;
    }

    /** Relanza el error de Oracle si la sesion no se pudo verificar. */
    static void exigirVerificada(SecurityContext seguridad) {
        if (seguridad instanceof SesionNoVerificada noVerificada) {
            throw noVerificada.causa();
        }
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        return false;
    }

    @Override
    public boolean isSecure() {
        return seguro;
    }

    @Override
    public String getAuthenticationScheme() {
        return null;
    }
}
