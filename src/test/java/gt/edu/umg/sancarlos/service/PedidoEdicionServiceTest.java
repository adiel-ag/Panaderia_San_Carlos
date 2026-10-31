package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.PedidoDAO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDetalleDTO;
import gt.edu.umg.sancarlos.dto.EditarPedidoDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.PedidoConflictoException;
import gt.edu.umg.sancarlos.exception.PedidoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.PedidoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Edición transaccional con DAO y conexión falsos. */
class PedidoEdicionServiceTest {
    private final List<String> eventos = new ArrayList<>();
    private final DaoFalso dao = new DaoFalso();
    private final UsuarioSesion admin = new UsuarioSesion(1, "a", "Admin", "ADMINISTRADOR", null);

    @Test void reemplazaDetallesYConservaEstadoEnUnaTransaccion() {
        Pedido resultado = servicio().editarPedido(50, solicitud(), admin);
        assertEquals("CONFIRMADO", resultado.getEstado());
        assertEquals(List.of("abrir", "setAutoCommit", "bloquear", "produccion", "producto:9",
                "reemplazar", "buscar", "commit", "close"), eventos);
    }

    @Test void sucursalAjenaVe404YNoEscribe() {
        UsuarioSesion ajena = new UsuarioSesion(2, "s", "Sucursal", "SUCURSAL", 7L);
        assertThrows(PedidoNoEncontradoException.class,
                () -> servicio().editarPedido(50, solicitud(), ajena));
        assertEquals(List.of("abrir", "setAutoCommit", "bloquear", "rollback", "close"), eventos);
    }

    @Test void estadoNoEditableYAsociacionSonConflictos() {
        dao.estado = "EN_PRODUCCION";
        assertEquals("Solo se pueden editar pedidos pendientes o confirmados",
                assertThrows(PedidoConflictoException.class,
                        () -> servicio().editarPedido(50, solicitud(), admin)).getMessage());
        eventos.clear();
        dao.estado = "CONFIRMADO";
        dao.asociado = true;
        assertEquals("El pedido ya está en una producción; quítelo o cancele la producción primero",
                assertThrows(PedidoConflictoException.class,
                        () -> servicio().editarPedido(50, solicitud(), admin)).getMessage());
        assertFalse(eventos.contains("reemplazar"));
    }

    @Test void pedidoInexistenteYProductoInactivoNoEscriben() {
        dao.existe = false;
        assertThrows(PedidoNoEncontradoException.class,
                () -> servicio().editarPedido(50, solicitud(), admin));
        eventos.clear();
        dao.existe = true;
        dao.producto = new PedidoDAO.ProductoPedido("Pan", "PRODUCTO_FINAL", "INACTIVO");
        assertEquals("El producto \"Pan\" no está disponible para pedidos",
                assertThrows(PedidoConflictoException.class,
                        () -> servicio().editarPedido(50, solicitud(), admin)).getMessage());
        assertFalse(eventos.contains("reemplazar"));
    }

    @Test void validaFechaObservacionLineasProductosYCantidadAntesDeAbrir() {
        List<EditarPedidoDTO> invalidas = List.of(
                new EditarPedidoDTO(LocalDate.now().minusDays(1), null, List.of(linea(9, "1"))),
                new EditarPedidoDTO(LocalDate.now().plusDays(1), "x".repeat(501), List.of(linea(9, "1"))),
                new EditarPedidoDTO(LocalDate.now().plusDays(1), null, List.of()),
                new EditarPedidoDTO(LocalDate.now().plusDays(1), null, List.of(linea(9, "1"), linea(9, "2"))),
                new EditarPedidoDTO(LocalDate.now().plusDays(1), null, List.of(linea(9, "0"))),
                new EditarPedidoDTO(LocalDate.now().plusDays(1), null, List.of(linea(9, "1.0001"))));
        for (EditarPedidoDTO invalida : invalidas) {
            assertThrows(PedidoSolicitudInvalidaException.class,
                    () -> servicio().editarPedido(50, invalida, admin));
        }
        assertTrue(eventos.isEmpty());
    }

    @Test void errorAlReemplazarRevierteTodo() {
        dao.error = new SQLException("fallo", "42000", 942);
        assertThrows(DataAccessException.class,
                () -> servicio().editarPedido(50, solicitud(), admin));
        assertTrue(eventos.contains("rollback"));
        assertFalse(eventos.contains("commit"));
    }

    private PedidoService servicio() { return new PedidoService(dao, this::conexion); }
    private Connection conexion() {
        eventos.add("abrir");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (List.of("setAutoCommit", "commit", "rollback", "close").contains(method.getName()))
                        eventos.add(method.getName());
                    return null;
                });
    }
    private static CrearPedidoDetalleDTO linea(long id, String cantidad) {
        CrearPedidoDetalleDTO linea = new CrearPedidoDetalleDTO();
        linea.setIdProducto(id);
        linea.setCantidad(new BigDecimal(cantidad));
        return linea;
    }
    private static EditarPedidoDTO solicitud() {
        return new EditarPedidoDTO(LocalDate.now().plusDays(1), "  Entregar  ",
                List.of(linea(9, "2.5")));
    }
    private final class DaoFalso extends PedidoDAO {
        String estado = "CONFIRMADO";
        boolean existe = true;
        boolean asociado;
        ProductoPedido producto = new ProductoPedido("Pan", "PRODUCTO_FINAL", "ACTIVO");
        SQLException error;
        @Override public Optional<PedidoBloqueado> bloquear(Connection c, long id) {
            eventos.add("bloquear");
            return existe ? Optional.of(new PedidoBloqueado(estado, 3)) : Optional.empty();
        }
        @Override public boolean estaEnProduccion(Connection c, long id) {
            eventos.add("produccion"); return asociado;
        }
        @Override public Optional<ProductoPedido> productoParaPedido(Connection c, long id) {
            eventos.add("producto:" + id); return Optional.ofNullable(producto);
        }
        @Override public void reemplazarDetalles(Connection c, long id, EditarPedidoDTO solicitud)
                throws SQLException {
            eventos.add("reemplazar"); if (error != null) throw error;
        }
        @Override public Optional<Pedido> buscarPorId(Connection c, long id) {
            eventos.add("buscar");
            Pedido pedido = new Pedido(); pedido.setIdPedido(id); pedido.setEstado(estado);
            return Optional.of(pedido);
        }
    }
}
