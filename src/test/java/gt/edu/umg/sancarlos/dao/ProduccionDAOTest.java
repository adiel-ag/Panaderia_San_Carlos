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
        new ProduccionDAO().cancelar(conexion("PLANIFICADA"), 90);

        assertEquals(List.of(
                "SELECT estado FROM PRODUCCION WHERE id_produccion = ? FOR UPDATE",
                "DELETE FROM PRODUCCION_PEDIDO WHERE id_produccion = ?",
                "UPDATE PRODUCCION SET estado = 'CANCELADA' WHERE id_produccion = ?"), sentencias);
        assertEquals(List.of(90L, 90L, 90L), parametros);
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
    private Connection conexion(String estado) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        sentencias.add((String) args[0]);
                        return sentencia(estado);
                    }
                    return null;
                });
    }

    private PreparedStatement sentencia(String estado) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setLong" -> {
                        parametros.add(args[1]);
                        yield null;
                    }
                    case "executeQuery" -> fila(estado);
                    case "executeUpdate" -> 1;
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
