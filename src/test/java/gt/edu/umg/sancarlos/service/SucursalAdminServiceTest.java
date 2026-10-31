package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.SucursalAdminDAO;
import gt.edu.umg.sancarlos.dto.SucursalAdminDTO;
import gt.edu.umg.sancarlos.dto.SucursalSolicitudDTO;
import gt.edu.umg.sancarlos.exception.CatalogoConflictoException;
import gt.edu.umg.sancarlos.exception.CatalogoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.CatalogoSolicitudInvalidaException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reglas de sucursales sin conexión a Oracle. */
class SucursalAdminServiceTest {
    private final List<String> eventos = new ArrayList<>();
    private final Falso dao = new Falso();

    private SucursalAdminService servicio() {
        return new SucursalAdminService(dao, () -> (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class[]{Connection.class}, (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "setAutoCommit" -> eventos.add("transaccion");
                        case "commit" -> eventos.add("commit");
                        case "rollback" -> eventos.add("rollback");
                        case "close" -> eventos.add("close");
                        default -> { }
                    }
                    return null;
                }));
    }
    private static SucursalSolicitudDTO solicitud(String nombre, String direccion, String telefono) {
        return new SucursalSolicitudDTO(nombre, direccion, telefono);
    }

    @Test void validaCamposAntesDeAbrirConexion() {
        for (SucursalSolicitudDTO s : List.of(solicitud(" ", "", ""),
                solicitud("x".repeat(101), "", ""), solicitud("Centro", "x".repeat(201), ""),
                solicitud("Centro", "", "1".repeat(21)), solicitud("Centro", "", "abc"))) {
            assertThrows(CatalogoSolicitudInvalidaException.class, () -> servicio().crear(s));
        }
        assertTrue(eventos.isEmpty());
    }

    @Test void creaYEditaConCamposRecortados() {
        servicio().crear(solicitud(" Centro ", " Calle 1 ", " +502 12-34 "));
        assertEquals("Centro", dao.nombre);
        assertEquals("Calle 1", dao.direccion);
        assertEquals("+502 12-34", dao.telefono);
        servicio().actualizar(7, solicitud(" Norte ", "", "(123)"));
        assertEquals("Norte", dao.nombre);
        assertEquals("(123)", dao.telefono);
        assertTrue(eventos.contains("commit"));
    }

    @Test void noDesactivaConUsuariosActivosOPedidosAbiertos() {
        for (SucursalAdminDTO dato : List.of(new SucursalAdminDTO(7, "Centro", null, null, "ACTIVO", 2, 0),
                new SucursalAdminDTO(7, "Centro", null, null, "ACTIVO", 0, 3))) {
            dao.actual = dato;
            CatalogoConflictoException error = assertThrows(CatalogoConflictoException.class,
                    () -> servicio().cambiarEstado(7, "INACTIVO"));
            assertEquals("No se puede desactivar: tiene " + dato.usuariosActivos()
                    + " usuarios activos y " + dato.pedidosAbiertos() + " pedidos sin despachar", error.getMessage());
        }
        assertNull(dao.estado);
        assertTrue(eventos.contains("rollback"));
    }

    @Test void activaYDesactivaSucursalLibre() {
        servicio().cambiarEstado(7, "INACTIVO");
        assertEquals("INACTIVO", dao.estado);
        servicio().cambiarEstado(7, "ACTIVO");
        assertEquals("ACTIVO", dao.estado);
    }

    @Test void duplicadoNoEncontradoYEstadoInvalido() {
        dao.duplicado = true;
        assertEquals("Ya existe una sucursal con ese nombre", assertThrows(CatalogoConflictoException.class,
                () -> servicio().crear(solicitud("Centro", "", ""))).getMessage());
        assertTrue(eventos.contains("rollback"));
        dao.existe = false;
        assertThrows(CatalogoNoEncontradoException.class,
                () -> servicio().actualizar(7, solicitud("Centro", "", "")));
        assertThrows(CatalogoSolicitudInvalidaException.class,
                () -> servicio().cambiarEstado(7, "OTRO"));
    }

    private final class Falso extends SucursalAdminDAO {
        SucursalAdminDTO actual = new SucursalAdminDTO(7, "Centro", null, null, "ACTIVO", 0, 0);
        boolean duplicado, existe = true;
        String nombre, direccion, telefono, estado;
        @Override public List<SucursalAdminDTO> listar(Connection c) { return List.of(actual); }
        @Override public Optional<SucursalAdminDTO> buscarPorId(Connection c, long id) { return Optional.of(actual); }
        @Override public boolean bloquear(Connection c, long id) { eventos.add("bloquear"); return existe; }
        @Override public long crear(Connection c, String n, String d, String t) throws SQLException {
            if (duplicado) throw new SQLException("único", "23000", 1);
            nombre = n; direccion = d; telefono = t; return 7;
        }
        @Override public void actualizar(Connection c, long id, String n, String d, String t) {
            nombre = n; direccion = d; telefono = t;
        }
        @Override public void cambiarEstado(Connection c, long id, String e) { estado = e; }
    }
}
