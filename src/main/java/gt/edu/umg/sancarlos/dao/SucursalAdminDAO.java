package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.SucursalAdminDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Lectura y escritura de sucursales para administración. */
public class SucursalAdminDAO {
    private static final String COLUMNAS = "SELECT s.id_sucursal, s.nombre, s.direccion, s.telefono, "
            + "s.estado, (SELECT COUNT(*) FROM USUARIO u WHERE u.id_sucursal = s.id_sucursal "
            + "AND u.estado = 'ACTIVO') AS usuarios_activos, "
            + "(SELECT COUNT(*) FROM PEDIDO p WHERE p.id_sucursal = s.id_sucursal "
            + "AND p.estado IN ('PENDIENTE','CONFIRMADO','EN_PRODUCCION','LISTO_DESPACHO')) "
            + "AS pedidos_abiertos FROM SUCURSAL s";

    public List<SucursalAdminDTO> listar(Connection connection) throws SQLException {
        List<SucursalAdminDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(COLUMNAS + " ORDER BY s.nombre")) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(mapear(rows));
            }
        }
        return items;
    }

    public Optional<SucursalAdminDTO> buscarPorId(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COLUMNAS + " WHERE s.id_sucursal = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapear(rows)) : Optional.empty();
            }
        }
    }

    public boolean bloquear(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_sucursal FROM SUCURSAL WHERE id_sucursal = ? FOR UPDATE")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public long crear(Connection connection, String nombre, String direccion, String telefono)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO SUCURSAL (nombre, direccion, telefono, estado) "
                + "VALUES (?, ?, ?, 'ACTIVO')", new String[]{"ID_SUCURSAL"})) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, nombre);
            statement.setString(2, direccion);
            statement.setString(3, telefono);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("No se obtuvo el ID de la sucursal");
    }

    public void actualizar(Connection connection, long id, String nombre, String direccion,
            String telefono) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE SUCURSAL SET nombre = ?, direccion = ?, telefono = ? WHERE id_sucursal = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, nombre);
            statement.setString(2, direccion);
            statement.setString(3, telefono);
            statement.setLong(4, id);
            statement.executeUpdate();
        }
    }

    public void cambiarEstado(Connection connection, long id, String estado) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE SUCURSAL SET estado = ? WHERE id_sucursal = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, estado);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private static SucursalAdminDTO mapear(ResultSet rows) throws SQLException {
        return new SucursalAdminDTO(rows.getLong("id_sucursal"), rows.getString("nombre"),
                rows.getString("direccion"), rows.getString("telefono"), rows.getString("estado"),
                rows.getLong("usuarios_activos"), rows.getLong("pedidos_abiertos"));
    }
}
