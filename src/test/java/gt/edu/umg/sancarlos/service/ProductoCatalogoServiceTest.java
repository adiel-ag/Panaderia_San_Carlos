package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.ProductoDAO;
import gt.edu.umg.sancarlos.dto.ProductoSolicitudDTO;
import gt.edu.umg.sancarlos.exception.CatalogoConflictoException;
import gt.edu.umg.sancarlos.exception.CatalogoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.CatalogoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.Producto;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reglas de productos con DAO y conexión falsos. */
class ProductoCatalogoServiceTest {
    private final List<String> eventos = new ArrayList<>();
    private final Falso dao = new Falso();

    private ProductoService servicio() {
        return new ProductoService(dao, () -> (Connection) Proxy.newProxyInstance(
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
    private static ProductoSolicitudDTO solicitud(String nombre, long tipo, long unidad, String minimo) {
        return new ProductoSolicitudDTO(nombre, tipo, unidad, new BigDecimal(minimo));
    }
    private static Producto producto(boolean enUso) {
        Producto p = new Producto();
        p.setIdProducto(5);
        p.setIdTipoProducto(1);
        p.setIdUnidad(2);
        p.setEnUso(enUso);
        return p;
    }

    @Test void validaNombreYNumeroAntesDeAbrirConexion() {
        for (ProductoSolicitudDTO solicitud : List.of(solicitud(" ", 1, 2, "0"),
                solicitud("x".repeat(101), 1, 2, "0"), solicitud("Pan", 1, 2, "-1"),
                solicitud("Pan", 1, 2, "0.0001"), solicitud("Pan", 1, 2, "1000000000"),
                solicitud("Pan", 0, 2, "0"), solicitud("Pan", 1, 0, "0"))) {
            assertThrows(CatalogoSolicitudInvalidaException.class, () -> servicio().crear(solicitud));
        }
        assertTrue(eventos.isEmpty());
    }

    @Test void creaConNombreRecortadoYTransaccion() {
        Producto creado = servicio().crear(solicitud(" Pan ", 1, 2, "0.125"));
        assertEquals(5, creado.getIdProducto());
        assertEquals(" Pan ".trim(), dao.nombre);
        assertEquals(List.of("transaccion", "commit", "close"), eventos);
    }

    @Test void rechazaTipoYUnidadInexistentes() {
        dao.tipoExiste = false;
        assertThrows(CatalogoSolicitudInvalidaException.class,
                () -> servicio().crear(solicitud("Pan", 1, 2, "0")));
        assertTrue(eventos.contains("rollback"));
        dao.tipoExiste = true;
        dao.unidadExiste = false;
        assertThrows(CatalogoSolicitudInvalidaException.class,
                () -> servicio().crear(solicitud("Pan", 1, 2, "0")));
    }

    @Test void productoUsadoNoPermiteCambiarTipoOUnidad() {
        dao.actual = producto(true);
        for (ProductoSolicitudDTO solicitud : List.of(solicitud("Pan", 2, 2, "0"),
                solicitud("Pan", 1, 3, "0"))) {
            CatalogoConflictoException error = assertThrows(CatalogoConflictoException.class,
                    () -> servicio().actualizar(5, solicitud));
            assertEquals("No se puede cambiar el tipo o la unidad de un producto que ya se usó", error.getMessage());
        }
        assertNull(dao.nombre);
        assertTrue(eventos.contains("rollback"));
    }

    @Test void productoUsadoPermiteEditarNombreYMinimo() {
        dao.actual = producto(true);
        servicio().actualizar(5, solicitud("Nuevo", 1, 2, "3.5"));
        assertEquals("Nuevo", dao.nombre);
        assertTrue(eventos.contains("commit"));
    }

    @Test void productoSinUsoPermiteCambiarTipoYUnidad() {
        dao.actual = producto(false);
        servicio().actualizar(5, solicitud("Nuevo", 2, 3, "0"));
        assertEquals(2, dao.tipo);
        assertEquals(3, dao.unidad);
    }

    @Test void duplicadoEs409YRevierte() {
        dao.duplicado = true;
        assertEquals("Ya existe un producto con ese nombre", assertThrows(CatalogoConflictoException.class,
                () -> servicio().crear(solicitud("Pan", 1, 2, "0"))).getMessage());
        assertTrue(eventos.contains("rollback"));
    }

    @Test void estadoValidoPermiteDesactivarYNoEncontradoEs404() {
        servicio().cambiarEstado(5, "INACTIVO");
        assertEquals("INACTIVO", dao.estado);
        assertThrows(CatalogoSolicitudInvalidaException.class, () -> servicio().cambiarEstado(5, "BAJA"));
        dao.actual = null;
        assertThrows(CatalogoNoEncontradoException.class, () -> servicio().cambiarEstado(5, "ACTIVO"));
    }

    private final class Falso extends ProductoDAO {
        Producto actual = producto(false);
        boolean tipoExiste = true, unidadExiste = true, duplicado;
        String nombre, estado;
        long tipo, unidad;
        @Override public boolean existeTipo(Connection c, long id) { return tipoExiste; }
        @Override public boolean existeUnidad(Connection c, long id) { return unidadExiste; }
        @Override public Optional<Producto> bloquear(Connection c, long id) { return Optional.ofNullable(actual); }
        @Override public Optional<Producto> buscarPorId(Connection c, long id) { return Optional.ofNullable(actual); }
        @Override public long crear(Connection c, String n, long t, long u, BigDecimal m) throws SQLException {
            if (duplicado) throw new SQLException("único", "23000", 1);
            nombre = n; tipo = t; unidad = u; return 5;
        }
        @Override public void actualizar(Connection c, long id, String n, long t, long u, BigDecimal m) {
            nombre = n; tipo = t; unidad = u;
        }
        @Override public void cambiarEstado(Connection c, long id, String e) { estado = e; }
    }
}
