package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.InventarioConflictoException;
import gt.edu.umg.sancarlos.exception.InventarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.InventarioSolicitudInvalidaException;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas sin Oracle: validaciones previas y traducción de códigos de PKG_INVENTARIO. */
class InventarioServiceTest {

    private final InventarioService service = new InventarioService();

    @Test
    void validaDatosAntesDeAbrirConexion() {
        assertThrows(IllegalArgumentException.class, () -> service.consultarExistencia(0));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarEntrada(0, 7, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarEntrada(35, 0, BigDecimal.ONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarEntrada(35, 7, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarSalida(35, 7, BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.registrarSalida(35, 7, BigDecimal.ONE, "x".repeat(501)));
    }

    @Test
    void movimientosManualesUsanOrigenAjuste() {
        assertEquals("AJUSTE", InventarioService.ORIGEN_AJUSTE);
    }

    @Test
    void traduceCodigosDeInventario() {
        assertInstanceOf(InventarioSolicitudInvalidaException.class, traducir(20001));
        assertInstanceOf(InventarioSolicitudInvalidaException.class, traducir(20002));
        assertInstanceOf(InventarioConflictoException.class, traducir(20003));
        assertInstanceOf(InventarioConflictoException.class, traducir(20004));
        assertInstanceOf(InventarioNoEncontradoException.class, traducir(2291));
        assertInstanceOf(DataAccessException.class, traducir(942));
    }

    @Test
    void conservaCausaYNoExponeTextoSql() {
        SQLException sql = new SQLException("ORA-20003: Inventario insuficiente ORA-06512", "72000", 20003);
        RuntimeException traducida = InventarioService.traducirErrorOracle("mensaje", sql);
        assertSame(sql, traducida.getCause());
        assertFalse(traducida.getMessage().contains("ORA-"));
    }

    private static RuntimeException traducir(int codigo) {
        return InventarioService.traducirErrorOracle("mensaje",
                new SQLException("ORA-" + codigo, "72000", codigo));
    }
}
