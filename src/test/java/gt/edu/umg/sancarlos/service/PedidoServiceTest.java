package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.PedidoDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.PedidoConflictoException;
import gt.edu.umg.sancarlos.exception.PedidoNoEncontradoException;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cancelacion de pedidos sin Oracle: DAO y conexion falsos. */
class PedidoServiceTest {

    private static final UsuarioSesion ADMIN =
            new UsuarioSesion(1, "admin", "Admin", "ADMINISTRADOR", null);

    private final List<String> eventos = new ArrayList<>();
    private final PedidoDAOFalso dao = new PedidoDAOFalso();

    @Test
    void cancelaEnUnaTransaccionYDevuelveElPedidoActualizado() {
        Pedido cancelado = servicio().cancelarPedido(501, ADMIN);

        assertEquals("CANCELADO", cancelado.getEstado());
        assertEquals(List.of("abrir", "autoCommit:false", "buscar:501", "cancelar:501",
                "buscar:501", "commit", "close"), eventos);
    }

    @Test
    void pedidoQueYaNoPuedeCancelarseEsConflicto() {
        dao.error = new SQLException("ORA-20313: El pedido ya no puede cancelarse", "72000", 20313);

        PedidoConflictoException error = assertThrows(PedidoConflictoException.class,
                () -> servicio().cancelarPedido(501, ADMIN));

        assertEquals("El pedido ya no puede cancelarse", error.getMessage());
        assertSame(dao.error, error.getCause());
        assertEquals(List.of("abrir", "autoCommit:false", "buscar:501", "cancelar:501",
                "rollback", "close"), eventos);
    }

    @Test
    void pedidoEnUnaProduccionEsConflicto() {
        dao.error = new SQLException("ORA-20314: asociado ORA-06512", "72000", 20314);

        PedidoConflictoException error = assertThrows(PedidoConflictoException.class,
                () -> servicio().cancelarPedido(501, ADMIN));

        assertEquals("El pedido ya está asociado a una producción", error.getMessage());
        assertFalse(error.getMessage().contains("ORA-"));
        assertTrue(eventos.contains("rollback"), eventos.toString());
        assertFalse(eventos.contains("commit"), eventos.toString());
    }

    @Test
    void pedidoInexistenteEs404SinLlamarAlPaquete() {
        dao.pedido = null;

        PedidoNoEncontradoException error = assertThrows(PedidoNoEncontradoException.class,
                () -> servicio().cancelarPedido(501, ADMIN));

        assertEquals("No existe un pedido con ID 501", error.getMessage());
        assertFalse(eventos.contains("cancelar:501"), eventos.toString());
        assertTrue(eventos.contains("rollback"), eventos.toString());
    }

    @Test
    void sinDatosEnOracleTambienEs404() {
        dao.error = new SQLException("ORA-01403: no data found", "02000", 1403);

        assertThrows(PedidoNoEncontradoException.class,
                () -> servicio().cancelarPedido(501, ADMIN));
        assertTrue(eventos.contains("rollback"), eventos.toString());
    }

    @Test
    void errorNoReconocidoHaceRollbackYEsErrorDeDatos() {
        dao.error = new SQLException("ORA-00942", "42000", 942);

        DataAccessException error = assertThrows(DataAccessException.class,
                () -> servicio().cancelarPedido(501, ADMIN));

        assertEquals("No fue posible cancelar el pedido", error.getMessage());
        assertEquals(List.of("abrir", "autoCommit:false", "buscar:501", "cancelar:501",
                "rollback", "close"), eventos);
    }

    @Test
    void sucursalSoloCancelaPedidosDeSuSucursal() {
        UsuarioSesion propia = new UsuarioSesion(7, "s", "Sucursal", "SUCURSAL", 2L);
        assertEquals("CANCELADO", servicio().cancelarPedido(501, propia).getEstado());

        for (Long otra : new Long[]{1L, null}) {
            eventos.clear();
            dao.pedido = pedido("CONFIRMADO");
            UsuarioSesion ajena = new UsuarioSesion(7, "s", "Sucursal", "SUCURSAL", otra);
            PedidoNoEncontradoException error = assertThrows(PedidoNoEncontradoException.class,
                    () -> servicio().cancelarPedido(501, ajena));
            assertEquals("No existe un pedido con ID 501", error.getMessage());
            assertFalse(eventos.contains("cancelar:501"), eventos.toString());
        }
    }

    @Test
    void validaDatosAntesDeAbrirConexion() {
        assertThrows(IllegalArgumentException.class, () -> servicio().cancelarPedido(0, ADMIN));
        assertThrows(IllegalArgumentException.class, () -> servicio().cancelarPedido(501, null));
        assertTrue(eventos.isEmpty(), eventos.toString());
    }

    private PedidoService servicio() {
        return new PedidoService(dao, this::conexion);
    }

    private Connection conexion() {
        eventos.add("abrir");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> eventos.add("autoCommit:" + args[0]);
                        case "commit", "rollback", "close" -> eventos.add(method.getName());
                        default -> { }
                    }
                    return null;
                });
    }

    private static Pedido pedido(String estado) {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(501);
        pedido.setIdSucursal(2);
        pedido.setEstado(estado);
        return pedido;
    }

    private final class PedidoDAOFalso extends PedidoDAO {
        Pedido pedido = pedido("CONFIRMADO");
        SQLException error;

        @Override
        public Optional<Pedido> buscarPorId(Connection connection, long idPedido) {
            eventos.add("buscar:" + idPedido);
            return Optional.ofNullable(pedido);
        }

        @Override
        public void cancelar(Connection connection, long idPedido) throws SQLException {
            eventos.add("cancelar:" + idPedido);
            if (error != null) {
                throw error;
            }
            pedido = pedido("CANCELADO");
        }
    }
}
