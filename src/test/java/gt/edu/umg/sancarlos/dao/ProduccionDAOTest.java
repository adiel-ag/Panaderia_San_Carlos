package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Revisa el SQL de la cancelacion sin Oracle: conexion y sentencias falsas. */
class ProduccionDAOTest {

    private final List<String> sentencias = new ArrayList<>();
    private final List<Object> parametros = new ArrayList<>();

    @Test
    void cancelarBloqueaLiberaPedidosYMarcaCancelada() throws Exception {
        new ProduccionDAO().cancelar(conexion("PLANIFICADA", 22L, 23L), 90);

        assertEquals(List.of(
                "SELECT estado FROM PRODUCCION WHERE id_produccion = ? FOR UPDATE",
                "SELECT id_pedido FROM PRODUCCION_PEDIDO WHERE id_produccion = ? ORDER BY id_pedido",
                "DELETE FROM PRODUCCION_PEDIDO WHERE id_produccion = ?",
                "UPDATE PRODUCCION SET estado = 'CANCELADA', observacion = SUBSTR(TRIM(observacion || ' ' || ?), 1, 500) WHERE id_produccion = ?"),
                sentencias);
        // La observacion conserva que pedidos se liberaron.
        assertEquals(List.of(90L, 90L, 90L, "[Cancelada; pedidos liberados: #22, #23]", 90L), parametros);
    }

    @Test
    void notaSinPedidos() {
        assertEquals("[Cancelada sin pedidos]", ProduccionDAO.notaCancelacion(List.of()));
    }

    @Test
    void soloSeCancelaUnaProduccionPlanificada() {
        for (String estado : new String[]{"EN_PROCESO", "FINALIZADA", "CANCELADA"}) {
            sentencias.clear();
            ProduccionConflictoException error = assertThrows(ProduccionConflictoException.class,
                    () -> new ProduccionDAO().cancelar(conexion(estado), 90));
            assertEquals("Solo se puede cancelar una producción planificada", error.getMessage());
            assertEquals(1, sentencias.size(), estado);
        }
    }

    @Test
    void produccionInexistenteEs404() {
        ProduccionNoEncontradaException error = assertThrows(ProduccionNoEncontradaException.class,
                () -> new ProduccionDAO().cancelar(conexion(null), 90));
        assertEquals("No existe una producción con ID 90", error.getMessage());
        assertEquals(1, sentencias.size());
    }

    /** estado null simula que la producción no existe. */
    private Connection conexion(String estado, Long... pedidos) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        String sql = (String) args[0];
                        sentencias.add(sql);
                        return sentencia(sql.contains("FROM PRODUCCION_PEDIDO") && sql.startsWith("SELECT")
                                ? new ArrayList<>(List.of(pedidos)) : null, estado);
                    }
                    return null;
                });
    }

    /** pedidos != null: la consulta devuelve esos ID; si no, una fila con el estado. */
    private PreparedStatement sentencia(List<Long> pedidos, String estado) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setLong", "setString" -> {
                        parametros.add(args[1]);
                        yield null;
                    }
                    case "executeQuery" -> pedidos != null ? filasPedidos(pedidos) : fila(estado);
                    case "executeUpdate" -> 1;
                    default -> null;
                });
    }

    private static ResultSet filasPedidos(List<Long> pedidos) {
        int[] posicion = {-1};
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> ++posicion[0] < pedidos.size();
                    case "getLong" -> pedidos.get(posicion[0]);
                    default -> null;
                });
    }

    private static ResultSet fila(String estado) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> estado != null;
                    case "getString" -> estado;
                    default -> null;
                });
    }
}
