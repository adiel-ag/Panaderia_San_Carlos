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
    private static final String SQL_USUARIO =
            "SELECT id_sucursal FROM USUARIO WHERE id_usuario = ? FOR UPDATE";
    private static final String SQL_ESTADO_SUCURSAL =
            "SELECT estado FROM SUCURSAL WHERE id_sucursal = ? FOR UPDATE";

    private BloqueoSucursal() {
    }

    /** Si la sucursal no existe no bloquea nada; Oracle valida despues. */
    public static void bloquear(Connection connection, Long idSucursal) throws SQLException {
        if (idSucursal != null) {
            ejecutar(connection, SQL_SUCURSAL, idSucursal);
        }
    }

    /**
     * Bloquea primero al usuario (nadie le cambia la sucursal mientras tanto) y
     * despues su sucursal; devuelve el estado de esta o null si no tiene. Siempre
     * en ese orden, tambien al editar, para que dos operaciones no se esperen
     * mutuamente.
     */
    public static String bloquearDeUsuario(Connection connection, long idUsuario)
            throws SQLException {
        Long idSucursal = bloquearUsuario(connection, idUsuario);
        if (idSucursal == null) return null;
        try (PreparedStatement statement = connection.prepareStatement(SQL_ESTADO_SUCURSAL)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idSucursal);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    /** Bloquea la fila del usuario y devuelve su sucursal actual (null si no tiene). */
    public static Long bloquearUsuario(Connection connection, long idUsuario) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_USUARIO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idUsuario);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                long id = rows.getLong(1);
                return rows.wasNull() ? null : id;
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
