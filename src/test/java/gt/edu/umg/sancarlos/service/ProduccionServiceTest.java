package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.exception.ProduccionSolicitudInvalidaException;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static RuntimeException traducir(int codigo) {
        return ProduccionService.traducirErrorOracle("mensaje",
                new SQLException("ORA-" + codigo, "72000", codigo));
    }
}
