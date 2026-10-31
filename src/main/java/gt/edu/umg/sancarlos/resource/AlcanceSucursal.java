package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.util.Objects;

/**
 * SUCURSAL solo ve recursos de su sucursal. Los ajenos responden 404 como los
 * inexistentes para no revelar su existencia.
 */
final class AlcanceSucursal {

    private AlcanceSucursal() {
    }

    static boolean puedeVer(UsuarioSesion usuario, Long idSucursalDelRecurso) {
        return !Roles.SUCURSAL.equals(usuario.getRol())
                || (usuario.getIdSucursal() != null
                        && Objects.equals(usuario.getIdSucursal(), idSucursalDelRecurso));
    }

    /** Evita consultar pedidos cuando el filtro contradice la sucursal de sesion. */
    static boolean puedeListar(UsuarioSesion usuario, Long filtro) {
        return !Roles.SUCURSAL.equals(usuario.getRol())
                || (usuario.getIdSucursal() != null
                        && (filtro == null || Objects.equals(usuario.getIdSucursal(), filtro)));
    }

    static Long sucursalEfectiva(UsuarioSesion usuario, Long filtro) {
        return Roles.SUCURSAL.equals(usuario.getRol()) ? usuario.getIdSucursal() : filtro;
    }
}
