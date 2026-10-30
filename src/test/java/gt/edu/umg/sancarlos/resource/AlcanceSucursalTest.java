package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.UsuarioSesion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlcanceSucursalTest {

    @Test
    void otrosRolesVenCualquierSucursalInclusoNula() {
        for (String rol : new String[]{Roles.ADMINISTRADOR, Roles.GERENTE,
                Roles.PRODUCCION, Roles.DESPACHO}) {
            UsuarioSesion usuario = usuario(rol, null);
            assertTrue(AlcanceSucursal.puedeVer(usuario, 1L), rol);
            assertTrue(AlcanceSucursal.puedeVer(usuario, null), rol);
        }
    }

    @Test
    void sucursalSoloVeLaPropia() {
        UsuarioSesion usuario = usuario(Roles.SUCURSAL, 2L);
        assertTrue(AlcanceSucursal.puedeVer(usuario, 2L));
        assertFalse(AlcanceSucursal.puedeVer(usuario, 1L));
        assertFalse(AlcanceSucursal.puedeVer(usuario, null));
    }

    @Test
    void sucursalSinAsignacionNoVeRecursos() {
        UsuarioSesion usuario = usuario(Roles.SUCURSAL, null);
        assertFalse(AlcanceSucursal.puedeVer(usuario, 1L));
        assertFalse(AlcanceSucursal.puedeVer(usuario, null));
    }

    private static UsuarioSesion usuario(String rol, Long idSucursal) {
        return new UsuarioSesion(7, "u", "Usuario", rol, idSucursal);
    }
}
