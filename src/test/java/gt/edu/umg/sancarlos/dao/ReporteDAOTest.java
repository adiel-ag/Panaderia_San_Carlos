package gt.edu.umg.sancarlos.dao;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forma de las consultas de reportes y alertas, con JDBC falso (sin Oracle). */
class ReporteDAOTest {

    private final List<String> consultas = new ArrayList<>();
    private final List<Object> parametros = new ArrayList<>();
    private final List<List<Map<String, Object>>> respuestas = new ArrayList<>();
    private int timeouts;

    private Connection conexion() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        consultas.add((String) args[0]);
                        List<Map<String, Object>> filas = respuestas.isEmpty()
                                ? List.of() : respuestas.remove(0);
                        return sentencia(filas);
                    }
                    return null;
                });
    }

    private PreparedStatement sentencia(List<Map<String, Object>> filas) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setQueryTimeout" -> { timeouts++; yield null; }
                    case "setLong", "setDate" -> { parametros.add(args[1]); yield null; }
                    case "setNull" -> { parametros.add("NULL"); yield null; }
                    case "executeQuery" -> filas(filas);
                    default -> null;
                });
    }

    private static ResultSet filas(List<Map<String, Object>> filas) {
        int[] posicion = {-1};
        boolean[] nulo = {false};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "next": return ++posicion[0] < filas.size();
                        case "wasNull": return nulo[0];
                        case "close": return null;
                        default: break;
                    }
                    Object clave = args[0];
                    Object valor = clave instanceof Integer
                            ? filas.get(posicion[0]).values().iterator().next()
                            : filas.get(posicion[0]).get(clave);
                    nulo[0] = valor == null;
                    return switch (method.getName()) {
                        case "getLong" -> valor == null ? 0L : ((Number) valor).longValue();
                        default -> valor;
                    };
                });
    }

    @Test
    void pedidosPorSucursalExcluyeCanceladosUsaRangoInclusivoYAgrupaProductos() throws Exception {
        respuestas.add(List.of(
                Map.of("id_sucursal", 1L, "id_producto", 33L, "producto", "Pan francés",
                        "unidad", "UND", "cantidad", new BigDecimal("40")),
                Map.of("id_sucursal", 1L, "id_producto", 34L, "producto", "Pan dulce",
                        "unidad", "UND", "cantidad", new BigDecimal("10"))));
        respuestas.add(List.of(
                Map.of("id_sucursal", 1L, "sucursal", "Centro", "pedidos", 3L,
                        "despachados", 1L, "pendientes", 2L),
                Map.of("id_sucursal", 2L, "sucursal", "Norte", "pedidos", 1L,
                        "despachados", 0L, "pendientes", 1L)));
        var reporte = new ReporteDAO().pedidosPorSucursal(conexion(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(2, consultas.size());
        for (String sql : consultas) {
            assertTrue(sql.contains("p.estado <> 'CANCELADO'"), sql);
            assertTrue(sql.contains("p.fecha_entrega >= ? AND p.fecha_entrega < ?"), sql);
        }
        assertTrue(consultas.get(0).contains("SUM(d.cantidad)"));
        assertTrue(consultas.get(1).contains("SUM(CASE WHEN p.estado = 'DESPACHADO'"));
        assertTrue(consultas.get(1).contains("SUM(CASE WHEN p.estado <> 'DESPACHADO'"));
        assertTrue(consultas.get(1).contains("ORDER BY s.nombre"));
        // El límite superior es hasta + 1 día para incluir el último día completo.
        assertEquals(List.of(Date.valueOf("2026-10-01"), Date.valueOf("2026-11-01"),
                Date.valueOf("2026-10-01"), Date.valueOf("2026-11-01")), parametros);
        assertEquals(2, timeouts);
        assertEquals(2, reporte.size());
        assertEquals("Centro", reporte.get(0).sucursal());
        assertEquals(2, reporte.get(0).productos().size());
        assertEquals(2, reporte.get(0).pendientes());
        assertTrue(reporte.get(1).productos().isEmpty());
    }

    @Test
    void produccionPorDiaSoloFinalizadasAgrupadasPorDia() throws Exception {
        respuestas.add(List.of(Map.of("fecha", Date.valueOf("2026-10-05"), "id_producto", 33L,
                "producto", "Pan", "unidad", "UND", "planificado", new BigDecimal("50"),
                "producido", new BigDecimal("48"))));
        respuestas.add(List.of(Map.of("fecha", Date.valueOf("2026-10-05"), "producciones", 2L)));
        var reporte = new ReporteDAO().produccionPorDia(conexion(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
        assertEquals(2, consultas.size());
        for (String sql : consultas) {
            assertTrue(sql.contains("pr.estado = 'FINALIZADA'"), sql);
            assertTrue(sql.contains("TRUNC(pr.fecha_produccion)"), sql);
        }
        assertTrue(consultas.get(0).contains("SUM(pd.cantidad_planificada)"));
        assertTrue(consultas.get(0).contains("cantidad_producida"));
        assertEquals(Date.valueOf("2026-10-06"), parametros.get(1));
        assertEquals(2, timeouts);
        assertEquals(LocalDate.of(2026, 10, 5), reporte.get(0).fecha());
        assertEquals(2, reporte.get(0).producciones());
        assertEquals(new BigDecimal("48"), reporte.get(0).productos().get(0).producido());
    }

    @Test
    void consumoSumaSalidasDeProduccionDeInsumosOrdenadoDescendente() throws Exception {
        respuestas.add(List.of(Map.of("id_producto", 2L, "insumo", "Harina", "unidad", "KG",
                "consumido", new BigDecimal("12.5"))));
        var reporte = new ReporteDAO().consumoInsumos(conexion(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        String sql = consultas.get(0);
        assertTrue(sql.contains("FROM MOVIMIENTO_INVENTARIO m"));
        assertTrue(sql.contains("m.tipo_movimiento = 'SALIDA'"));
        assertTrue(sql.contains("m.origen = 'PRODUCCION'"));
        assertTrue(sql.contains("tp.nombre = 'INSUMO'"));
        assertTrue(sql.contains("SUM(m.cantidad)"));
        assertTrue(sql.contains("ORDER BY consumido DESC"));
        assertEquals(1, timeouts);
        assertEquals("Harina", reporte.get(0).insumo());
        assertEquals(new BigDecimal("12.5"), reporte.get(0).consumido());
    }

    @Test
    void pedidosAtrasadosFiltraEstadosAbiertosYSucursalOpcional() throws Exception {
        respuestas.add(List.of(Map.of("c", 4L)));
        respuestas.add(List.of(Map.of("c", 1L)));
        ReporteDAO dao = new ReporteDAO();
        assertEquals(4, dao.contarPedidosAtrasados(conexion(), null));
        assertEquals(1, dao.contarPedidosAtrasados(conexion(), 7L));
        String sql = consultas.get(0);
        assertTrue(sql.contains("p.fecha_entrega < TRUNC(SYSDATE)"));
        assertTrue(sql.contains("'PENDIENTE', 'CONFIRMADO', 'EN_PRODUCCION', 'LISTO_DESPACHO'"));
        assertFalse(sql.contains("'DESPACHADO'"));
        assertFalse(sql.contains("'CANCELADO'"));
        assertTrue(sql.contains("(? IS NULL OR p.id_sucursal = ?)"));
        assertEquals(List.of("NULL", "NULL", 7L, 7L), parametros);
        assertEquals(2, timeouts);
    }

    @Test
    void productosSinRecetaUsanLaCondicionVigenteYExigenExactamenteUna() throws Exception {
        respuestas.add(List.of(Map.of("c", 3L)));
        assertEquals(3, new ReporteDAO().contarProductosSinReceta(conexion()));
        String sql = consultas.get(0);
        assertTrue(sql.contains("p.estado = 'ACTIVO'"));
        assertTrue(sql.contains("tp.nombre = 'PRODUCTO_FINAL'"));
        assertTrue(sql.contains(RecetaDAO.vigente("r").trim()));
        assertTrue(sql.contains(") <> 1"));
        assertEquals(1, timeouts);
    }

    @Test
    void insumosBajoMinimoComparanExistenciaConStockMinimo() throws Exception {
        respuestas.add(List.of(Map.of("c", 2L)));
        assertEquals(2, new ReporteDAO().contarInsumosBajoMinimo(conexion()));
        String sql = consultas.get(0);
        assertTrue(sql.contains("tp.nombre = 'INSUMO'"));
        assertTrue(sql.contains("p.estado = 'ACTIVO'"));
        assertTrue(sql.contains("JOIN INVENTARIO i"));
        assertTrue(sql.contains("i.cantidad_actual < p.stock_minimo"));
        assertEquals(1, timeouts);
    }
}
