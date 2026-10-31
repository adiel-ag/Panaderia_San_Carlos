package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.PedidoDAO;
import gt.edu.umg.sancarlos.dao.ProduccionDAO;
import gt.edu.umg.sancarlos.dao.ProduccionDAO.FilaInsumo;
import gt.edu.umg.sancarlos.dto.InsumosProduccionDTO;
import gt.edu.umg.sancarlos.dto.ProduccionResumenDTO;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsumosProduccionTest {

    private static Connection conexion() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> null);
    }

    private static ProduccionService servicio(String estado, List<FilaInsumo> filas,
            boolean[] planificada) {
        ProduccionDAO dao = new ProduccionDAO() {
            @Override public Optional<ProduccionResumenDTO> buscarResumenPorId(Connection connection,
                    long id) {
                return estado == null ? Optional.empty() : Optional.of(new ProduccionResumenDTO(
                        id, 1, "Ana", LocalDateTime.of(2026, 10, 31, 8, 0), estado, null, 1));
            }
            @Override public List<FilaInsumo> consultarInsumos(Connection connection,
                    long id, boolean esPlanificada) {
                planificada[0] = esPlanificada;
                return filas;
            }
        };
        return new ProduccionService(dao, new PedidoDAO(), InsumosProduccionTest::conexion);
    }

    @Test
    void planificadaSumaRedondeaYDetectaRecetaAmbiguaEInventarioAusente() {
        boolean[] planificada = {false};
        List<FilaInsumo> filas = List.of(
                new FilaInsumo(10, "Pan", 1, 3L, "Harina", "kg",
                        new BigDecimal("1.2345"), new BigDecimal("2.000"), false),
                new FilaInsumo(11, "Pastel", 1, 3L, "Harina", "kg",
                        new BigDecimal("1.2345"), new BigDecimal("2.000"), false),
                new FilaInsumo(12, "Galleta", 2, null, null, null, null, null, true),
                new FilaInsumo(10, "Pan", 1, 4L, "Levadura", "g",
                        new BigDecimal("0.1255"), null, true));
        InsumosProduccionDTO resultado = servicio("PLANIFICADA", filas, planificada)
                .consultarInsumos(5);
        assertTrue(planificada[0]);
        assertFalse(resultado.suficiente());
        assertEquals("Galleta", resultado.productosSinReceta().get(0).nombre());
        assertEquals(new BigDecimal("2.469"), resultado.insumos().get(0).requerido());
        assertEquals(new BigDecimal("0.469"), resultado.insumos().get(0).faltante());
        assertEquals(new BigDecimal("0.126"), resultado.insumos().get(1).requerido());
        assertTrue(resultado.insumos().get(1).sinInventario());
    }

    @Test
    void unaFaltaMenorQueElRedondeoNoSeOcultaComoSuficiente() {
        // 1.0004 requerido contra 1.000 disponible: SP_SALIDA lo rechazaria.
        List<FilaInsumo> filas = List.of(new FilaInsumo(10, "Pan", 1, 3L, "Harina", "kg",
                new BigDecimal("1.0004"), new BigDecimal("1.000"), false));
        InsumosProduccionDTO resultado = servicio("EN_PROCESO", filas, new boolean[1])
                .consultarInsumos(5);
        assertFalse(resultado.suficiente());
        assertEquals(new BigDecimal("0.001"), resultado.insumos().get(0).faltante());
    }

    @Test
    void procesoUsaDetalleYCanceladaNoConsultaIngredientes() {
        boolean[] planificada = {true};
        FilaInsumo fila = new FilaInsumo(10, "Pan", 1, 3L, "Harina", "kg",
                BigDecimal.ONE, BigDecimal.TEN, false);
        InsumosProduccionDTO proceso = servicio("EN_PROCESO", List.of(fila), planificada)
                .consultarInsumos(5);
        assertFalse(planificada[0]);
        assertTrue(proceso.suficiente());
        assertEquals(0, proceso.insumos().get(0).faltante().signum());
        assertTrue(servicio("CANCELADA", List.of(fila), planificada)
                .consultarInsumos(5).insumos().isEmpty());
        assertEquals("FINALIZADA", servicio("FINALIZADA", List.of(fila), planificada)
                .consultarInsumos(5).estado());
        assertFalse(planificada[0]);
    }

    @Test
    void produccionAusenteEs404() {
        assertThrows(ProduccionNoEncontradaException.class,
                () -> servicio(null, List.of(), new boolean[1]).consultarInsumos(5));
    }
}
