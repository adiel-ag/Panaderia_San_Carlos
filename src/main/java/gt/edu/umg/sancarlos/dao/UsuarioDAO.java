package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.UsuarioDTO;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Escrituras mediante PKG_USUARIO y lecturas de administracion. */
public class UsuarioDAO {

    private static final String SQL_BASE = " FROM USUARIO u JOIN ROL r ON r.id_rol = u.id_rol "
            + "LEFT JOIN SUCURSAL s ON s.id_sucursal = u.id_sucursal "
            + "LEFT JOIN USUARIO_CREDENCIAL c ON c.id_usuario = u.id_usuario ";
    private static final String SQL_COLUMNAS = "SELECT u.id_usuario, u.nombre, u.username, "
            + "r.nombre AS rol, u.id_sucursal, s.nombre AS nombre_sucursal, u.estado, "
            + "u.fecha_creacion, c.ultimo_acceso, "
            + "CASE WHEN c.bloqueado_hasta > SYSTIMESTAMP THEN 1 ELSE 0 END AS bloqueado, "
            + "CASE WHEN c.id_usuario IS NULL THEN 0 ELSE 1 END AS tiene_clave ";
    private static final String SQL_FILTROS = " WHERE (? IS NULL OR r.nombre = ?) "
            + "AND (? IS NULL OR u.estado = ?) AND (? IS NULL OR u.id_sucursal = ?) ";

    public Optional<Long> buscarIdRolActivo(Connection connection, String nombre) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_rol FROM ROL WHERE nombre = ? AND estado = 'ACTIVO'")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, nombre);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getLong(1)) : Optional.empty();
            }
        }
    }

    public long crear(Connection connection, String nombre, String username, long idRol,
            Long idSucursal) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(
                "{ call PKG_USUARIO.SP_CREAR_USUARIO(?, ?, ?, ?, ?) }")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, nombre);
            statement.setString(2, username);
            statement.setLong(3, idRol);
            sucursal(statement, 4, idSucursal);
            statement.registerOutParameter(5, Types.NUMERIC);
            statement.execute();
            long id = statement.getLong(5);
            if (statement.wasNull()) {
                throw new SQLException("PKG_USUARIO.SP_CREAR_USUARIO no devolvio el ID");
            }
            return id;
        }
    }

    public void actualizar(Connection connection, long id, String nombre, long idRol,
            Long idSucursal) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(
                "{ call PKG_USUARIO.SP_ACTUALIZAR_USUARIO(?, ?, ?, ?) }")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            statement.setString(2, nombre);
            statement.setLong(3, idRol);
            sucursal(statement, 4, idSucursal);
            statement.execute();
        }
    }

    public void cambiarEstado(Connection connection, long id, String estado) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(
                "{ call PKG_USUARIO.SP_CAMBIAR_ESTADO(?, ?) }")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            statement.setString(2, estado);
            statement.execute();
        }
    }

    public void desbloquear(Connection connection, long id) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(
                "{ call PKG_USUARIO.SP_DESBLOQUEAR(?) }")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            statement.execute();
        }
    }

    public Optional<UsuarioDTO> buscarPorId(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SQL_COLUMNAS + SQL_BASE + " WHERE u.id_usuario = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapear(rows)) : Optional.empty();
            }
        }
    }

    public Pagina<UsuarioDTO> listar(Connection connection, int pagina, int tamano,
            String rol, String estado, Long idSucursal) throws SQLException {
        long total;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*)" + SQL_BASE + SQL_FILTROS)) {
            ConnectionFactory.aplicarTimeout(statement);
            filtros(statement, rol, estado, idSucursal);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                total = rows.getLong(1);
            }
        }
        List<UsuarioDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_COLUMNAS + SQL_BASE
                + SQL_FILTROS + " ORDER BY u.nombre, u.id_usuario OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            ConnectionFactory.aplicarTimeout(statement);
            filtros(statement, rol, estado, idSucursal);
            statement.setLong(7, ((long) pagina - 1) * tamano);
            statement.setInt(8, tamano);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(mapear(rows));
                }
            }
        }
        return new Pagina<>(items, total, pagina, tamano);
    }

    private static void filtros(PreparedStatement statement, String rol, String estado,
            Long idSucursal) throws SQLException {
        statement.setString(1, rol);
        statement.setString(2, rol);
        statement.setString(3, estado);
        statement.setString(4, estado);
        if (idSucursal == null) {
            statement.setNull(5, Types.NUMERIC);
            statement.setNull(6, Types.NUMERIC);
        } else {
            statement.setLong(5, idSucursal);
            statement.setLong(6, idSucursal);
        }
    }

    private static void sucursal(CallableStatement statement, int indice, Long id)
            throws SQLException {
        if (id == null) {
            statement.setNull(indice, Types.NUMERIC);
        } else {
            statement.setLong(indice, id);
        }
    }

    private static UsuarioDTO mapear(ResultSet rows) throws SQLException {
        long idSucursal = rows.getLong("id_sucursal");
        Long sucursal = rows.wasNull() ? null : idSucursal;
        return new UsuarioDTO(rows.getLong("id_usuario"), rows.getString("nombre"),
                rows.getString("username"), rows.getString("rol"), sucursal,
                rows.getString("nombre_sucursal"), rows.getString("estado"),
                fecha(rows.getTimestamp("fecha_creacion")),
                fecha(rows.getTimestamp("ultimo_acceso")),
                rows.getInt("bloqueado") == 1, rows.getInt("tiene_clave") == 1);
    }

    private static java.time.LocalDateTime fecha(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
