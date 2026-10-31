package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.PedidoDAO;
import gt.edu.umg.sancarlos.dao.ProduccionDAO;
import gt.edu.umg.sancarlos.dto.PedidoResumenDTO;
import gt.edu.umg.sancarlos.dto.ProduccionConPedidosDTO;
import gt.edu.umg.sancarlos.dto.ProduccionResumenDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.exception.ProduccionSolicitudInvalidaException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas sin Oracle: validaciones previas y traducción de códigos de PKG_PRODUCCION. */
class ProduccionServiceTest {

    private final ProduccionService service = new ProduccionService();

    @Test
    void validaIdsYCantidadAntesDeAbrirConexion() {
        assertThrows(IllegalArgumentException.class, () -> service.crearProduccion(0, null));
        assertThrows(IllegalArgumentException.class, () -> service.asociarPedido(0, 1));
        assertThrows(IllegalArgumentException.class, () -> service.asociarPedido(1, -1));
        assertThrows(IllegalArgumentException.class, () -> service.generarPlan(0));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarProducido(1, 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarProducido(1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarProducido(1, 1, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.finalizarProduccion(1, 0));
    }

    @Test
    void traduceCantidadInvalidaComoSolicitudInvalida() {
        assertInstanceOf(ProduccionSolicitudInvalidaException.class, traducir(20409));
    }

    @Test
    void traduceRegistrosInexistentesComoNoEncontrados() {
        for (int codigo : new int[]{1403, 20401, 20411}) {
            assertInstanceOf(ProduccionNoEncontradaException.class, traducir(codigo));
        }
    }

    @Test
    void traduceEstadosRecetasEInventarioComoConflicto() {
        int[] codigos = {20402, 20403, 20404, 20405, 20406, 20407, 20408, 20410, 20412, 20413,
            20201, 20202, 20003, 20004};
        for (int codigo : codigos) {
            assertInstanceOf(ProduccionConflictoException.class, traducir(codigo));
        }
    }

    @Test
    void conservaCausaYNoExponeTextoSql() {
        SQLException sql = new SQLException("ORA-20407: detalle interno ORA-06512", "72000", 20407);
        RuntimeException traducida = ProduccionService.traducirErrorOracle("mensaje", sql);
        assertSame(sql, traducida.getCause());
        assertFalse(traducida.getMessage().contains("ORA-"));
    }

    @Test
    void erroresNoReconocidosSiguenComoErrorDeAccesoADatos() {
        assertInstanceOf(DataAccessException.class, traducir(942));
    }

    @Test
    void cancelaEnUnaTransaccionYDevuelveLaProduccionSinPedidos() {
        ProduccionConPedidosDTO cancelada = servicioCancelacion(null).cancelarProduccion(90);

        assertEquals("CANCELADA", cancelada.estado());
        assertTrue(cancelada.pedidos().isEmpty());
        assertEquals(List.of("abrir", "autoCommit:false", "cancelar:90", "resumen:90",
                "pedidos:90", "commit", "close"), eventos);
    }

    @Test
    void cancelarProduccionNoPlanificadaEsConflictoConRollback() {
        ProduccionConflictoException error = assertThrows(ProduccionConflictoException.class,
                () -> servicioCancelacion(new ProduccionConflictoException(
                        "Solo se puede cancelar una producción planificada", null))
                        .cancelarProduccion(90));

        assertEquals("Solo se puede cancelar una producción planificada", error.getMessage());
        assertEquals(List.of("abrir", "autoCommit:false", "cancelar:90", "rollback", "close"),
                eventos);
    }

    @Test
    void cancelarProduccionInexistenteEs404ConRollback() {
        assertThrows(ProduccionNoEncontradaException.class, () -> servicioCancelacion(
                new ProduccionNoEncontradaException("No existe una producción con ID 90", null))
                .cancelarProduccion(90));
        assertTrue(eventos.contains("rollback"), eventos.toString());
        assertFalse(eventos.contains("commit"), eventos.toString());
    }

    @Test
    void errorSqlAlCancelarHaceRollback() {
        DataAccessException error = assertThrows(DataAccessException.class,
                () -> servicioCancelacion(new SQLException("ORA-00942", "42000", 942))
                        .cancelarProduccion(90));
        assertEquals("No fue posible cancelar la producción", error.getMessage());
        assertEquals(List.of("abrir", "autoCommit:false", "cancelar:90", "rollback", "close"),
                eventos);
        assertThrows(IllegalArgumentException.class, () -> service.cancelarProduccion(0));
    }

    private final List<String> eventos = new ArrayList<>();

    /** El DAO falso lanza el error indicado (o nada) al cancelar. */
    private ProduccionService servicioCancelacion(Exception error) {
        ProduccionDAO produccionDAO = new ProduccionDAO() {
            @Override
            public void cancelar(Connection connection, long idProduccion) throws SQLException {
                eventos.add("cancelar:" + idProduccion);
                if (error instanceof SQLException sql) throw sql;
                if (error instanceof RuntimeException runtime) throw runtime;
            }

            @Override
            public Optional<ProduccionResumenDTO> buscarResumenPorId(Connection connection,
                    long idProduccion) {
                eventos.add("resumen:" + idProduccion);
                return Optional.of(new ProduccionResumenDTO(idProduccion, 1, "Usuario",
                        LocalDateTime.of(2026, 10, 31, 8, 0), "CANCELADA", null, 0));
            }
        };
        PedidoDAO pedidoDAO = new PedidoDAO() {
            @Override
            public List<PedidoResumenDTO> listarDeProduccion(Connection connection,
                    long idProduccion) {
                eventos.add("pedidos:" + idProduccion);
                return List.of();
            }
        };
        return new ProduccionService(produccionDAO, pedidoDAO, this::conexion);
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

    private static RuntimeException traducir(int codigo) {
        return ProduccionService.traducirErrorOracle("mensaje",
                new SQLException("ORA-" + codigo, "72000", codigo));
    }
}
