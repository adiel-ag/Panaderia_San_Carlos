package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Bloquea la fila de la sucursal antes de darle usuarios activos o pedidos.
 * Desactivar una sucursal toma el mismo bloqueo y luego cuenta usuarios y
 * pedidos: asi ambas operaciones se hacen una despues de la otra y nunca queda
 * una sucursal inactiva con usuarios activos o pedidos nuevos.
 */
public final class BloqueoSucursal {

    private static final String SQL_SUCURSAL =
            "SELECT id_sucursal FROM SUCURSAL WHERE id_sucursal = ? FOR UPDATE";
    private static final String SQL_SUCURSAL_DE_USUARIO =
            "SELECT s.estado FROM SUCURSAL s JOIN USUARIO u ON u.id_sucursal = s.id_sucursal "
            + "WHERE u.id_usuario = ? FOR UPDATE OF s.id_sucursal";

    private BloqueoSucursal() {
    }

    /** Si la sucursal no existe no bloquea nada; Oracle valida despues. */
    public static void bloquear(Connection connection, Long idSucursal) throws SQLException {
        if (idSucursal != null) {
            ejecutar(connection, SQL_SUCURSAL, idSucursal);
        }
    }

    /** Bloquea la sucursal del usuario y devuelve su estado; null si no tiene sucursal. */
    public static String bloquearDeUsuario(Connection connection, long idUsuario)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_SUCURSAL_DE_USUARIO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idUsuario);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static void ejecutar(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
            }
        }
    }
}
