package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.DespachoConflictoException;
import gt.edu.umg.sancarlos.exception.DespachoNoEncontradoException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas sin Oracle: validaciones previas y traducción de códigos de PKG_DESPACHO. */
class DespachoServiceTest {

    private final DespachoService service = new DespachoService();

    @Test
    void validaDatosAntesDeAbrirConexion() {
        assertThrows(IllegalArgumentException.class, () -> service.despacharPedido(0, 7, null));
        assertThrows(IllegalArgumentException.class, () -> service.despacharPedido(501, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.despacharPedido(501, 7, "x".repeat(501)));
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorPedido(-1));
    }

    @Test
    void traduceEstadoEInventarioComoConflicto() {
        for (int codigo : new int[]{20501, 20502, 20003, 20004}) {
            assertInstanceOf(DespachoConflictoException.class, traducir(codigo));
        }
    }

    @Test
    void traducePedidoOUsuarioInexistenteComoNoEncontrado() {
        assertInstanceOf(DespachoNoEncontradoException.class, traducir(1403));
        assertInstanceOf(DespachoNoEncontradoException.class, traducir(2291));
        assertInstanceOf(DataAccessException.class, traducir(942));
    }

    @Test
    void conservaCausaYNoExponeTextoSql() {
        SQLException sql = new SQLException("ORA-20502: ya despachado ORA-06512", "72000", 20502);
        RuntimeException traducida = DespachoService.traducirErrorOracle("mensaje", sql);
        assertSame(sql, traducida.getCause());
        assertFalse(traducida.getMessage().contains("ORA-"));
    }

    private static RuntimeException traducir(int codigo) {
        return DespachoService.traducirErrorOracle("mensaje",
                new SQLException("ORA-" + codigo, "72000", codigo));
    }
}
