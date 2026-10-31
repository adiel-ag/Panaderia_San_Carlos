package gt.edu.umg.sancarlos.dao;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Revisa el SQL que arma RecetaDAO sin Oracle: conexion y sentencias falsas. */
class RecetaDAOTest {

    private static final String VIGENTE = "estado = 'ACTIVA' AND %1$s.fecha_inicio <= SYSDATE"
            + " AND (%1$s.fecha_fin IS NULL OR %1$s.fecha_fin >= TRUNC(SYSDATE))";

    private final List<String> sentencias = new ArrayList<>();
    private final List<Object> parametros = new ArrayList<>();

    @Test
    void elListadoUsaLaMismaCondicionQueFnRecetaActiva() throws Exception {
        new RecetaDAO().listarResumen(conexion());

        String sql = normalizar(sentencias.get(0));
        assertTrue(sql.contains("WHERE (rv." + String.format(VIGENTE, "rv") + ")"), sql);
        assertTrue(sql.contains("p.estado = 'ACTIVO' AND tp.nombre = 'PRODUCTO_FINAL'"), sql);
        assertTrue(sql.contains("ORDER BY p.nombre"), sql);
    }

    @Test
    void detalleYVersionesCalculanVigenteConLaMismaCondicion() throws Exception {
        RecetaDAO dao = new RecetaDAO();
        assertTrue(dao.buscarDetalle(conexion(), 9).isEmpty());
        dao.listarVersiones(conexion(), 33);

        for (String sql : List.of(sentencias.get(0), sentencias.get(1))) {
            assertTrue(normalizar(sql).contains("CASE WHEN (r." + String.format(VIGENTE, "r")
                    + ") THEN 1 ELSE 0 END"), sql);
        }
        assertTrue(normalizar(sentencias.get(1)).endsWith("ORDER BY r.version DESC"));
    }

    @Test
    void soloElGuardadoBloqueaElProducto() throws Exception {
        RecetaDAO dao = new RecetaDAO();
        dao.bloquearProducto(conexion(), 33);
        dao.buscarProducto(conexion(), 33);

        assertTrue(normalizar(sentencias.get(0)).endsWith(
                "WHERE p.id_producto = ? FOR UPDATE OF p.id_producto"));
        assertFalse(sentencias.get(1).contains("FOR UPDATE"));
    }

    @Test
    void buscaVariosProductosConUnMarcadorPorId() throws Exception {
        new RecetaDAO().buscarProductos(conexion(), Set.of(4L, 5L, 6L));

        assertTrue(normalizar(sentencias.get(0)).endsWith("WHERE p.id_producto IN (?, ?, ?)"));
        assertEquals(3, parametros.size());
    }

    @Test
    void cerrarVersionesConservaElHistorial() throws Exception {
        new RecetaDAO().desactivarActivas(conexion(), 33);

        assertEquals("UPDATE RECETA SET estado = 'INACTIVA', fecha_fin = SYSDATE "
                + "WHERE id_producto = ? AND estado = 'ACTIVA'", sentencias.get(0));
        assertEquals(List.of(33L), parametros);
    }

    private static String normalizar(String sql) {
        return sql.replaceAll("\\s+", " ").replace("( ", "(").trim();
    }

    private Connection conexion() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        sentencias.add((String) args[0]);
                        return sentencia();
                    }
                    return null;
                });
    }

    private PreparedStatement sentencia() {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setLong" -> {
                        parametros.add(args[1]);
                        yield null;
                    }
                    case "executeQuery" -> vacio();
                    case "executeUpdate" -> 0;
                    default -> null;
                });
    }

    private static ResultSet vacio() {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) ->
                        method.getName().equals("next") ? Boolean.FALSE : null);
    }
}
