package gt.edu.umg.sancarlos.dao;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultasBloque2Test {
    private final List<String> consultas = new ArrayList<>();
    private final List<Object> parametros = new ArrayList<>();
    private int timeouts;

    private Connection conexion() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        String sql = (String) args[0];
                        consultas.add(sql);
                        return sentencia(sql.startsWith("SELECT COUNT(*)"));
                    }
                    return null;
                });
    }

    private PreparedStatement sentencia(boolean conteo) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setQueryTimeout" -> { timeouts++; yield null; }
                    case "setLong", "setInt" -> { parametros.add(args[1]); yield null; }
                    case "executeQuery" -> filas(conteo);
                    default -> null;
                });
    }

    private static ResultSet filas(boolean conteo) {
        int[] posicion = {0};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> posicion[0]++ == 0 && conteo;
                    case "getLong" -> 0L;
                    default -> null;
                });
    }

    @Test
    void planificadaAgrupaPedidosYUsaSoloRecetaVigente() throws Exception {
        new ProduccionDAO().consultarInsumos(conexion(), 7, true);
        assertEquals(1, consultas.size());
        assertTrue(consultas.get(0).contains("SUM(d.cantidad)"));
        assertTrue(consultas.get(0).contains("GROUP BY d.id_producto"));
        assertTrue(consultas.get(0).contains("r.fecha_inicio <= SYSDATE"));
        assertTrue(consultas.get(0).contains("r.fecha_fin >= TRUNC(SYSDATE)"));
        assertTrue(consultas.get(0).contains("v.recetas_vigentes = 1"));
        assertTrue(consultas.get(0).contains("LEFT JOIN INVENTARIO"));
        assertEquals(List.of(7L), parametros);
        assertEquals(1, timeouts);
    }

    @Test
    void procesoUsaRecetaDelDetalleYCantidadProducida() throws Exception {
        new ProduccionDAO().consultarInsumos(conexion(), 7, false);
        assertEquals(1, consultas.size());
        assertTrue(consultas.get(0).contains("pd.id_receta"));
        assertTrue(consultas.get(0).contains("pd.cantidad_producida > 0"));
        assertTrue(consultas.get(0).contains("pd.cantidad_planificada"));
        assertEquals(1, timeouts);
    }

    @Test
    void movimientosPaginaOrdenaYAplicaTimeoutACadaConsulta() throws Exception {
        var pagina = new InventarioDAO().listarMovimientos(conexion(), 33, 2, 20);
        assertEquals(2, consultas.size());
        assertTrue(consultas.get(0).contains("COUNT(*)"));
        assertTrue(consultas.get(1).contains("LEFT JOIN USUARIO"));
        assertTrue(consultas.get(1).contains("m.fecha_movimiento DESC, m.id_movimiento DESC"));
        assertTrue(consultas.get(1).contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertEquals(List.of(33L, 33L, 20L, 20), parametros);
        assertEquals(2, timeouts);
        assertEquals(2, pagina.pagina());
    }
}
