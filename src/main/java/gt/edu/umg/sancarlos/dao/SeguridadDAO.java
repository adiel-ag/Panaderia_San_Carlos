package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

/** Acceso a PKG_SEGURIDAD y lectura del perfil de un usuario con sesion. */
public class SeguridadDAO {

    private static final String SP_ESTABLECER_CLAVE =
            "{ call PKG_SEGURIDAD.SP_ESTABLECER_CLAVE(?, ?, ?, ?, ?) }";
    private static final String SP_OBTENER_PARAMETROS =
            "{ call PKG_SEGURIDAD.SP_OBTENER_PARAMETROS_CLAVE(?, ?, ?, ?) }";
    private static final String SP_VALIDAR_ACCESO =
            "{ call PKG_SEGURIDAD.SP_VALIDAR_ACCESO(?, ?, ?, ?, ?, ?, ?) }";
    // Mismas condiciones que PKG_SEGURIDAD: usuario y rol ACTIVOS y con clave.
    private static final String SQL_PERFIL = """
            SELECT u.id_usuario,
                   u.username,
                   u.nombre,
                   r.nombre AS rol,
                   u.id_sucursal,
                   c.fecha_cambio,
                   c.ultimo_acceso
              FROM USUARIO u
              JOIN ROL r
                ON r.id_rol = u.id_rol
              JOIN USUARIO_CREDENCIAL c
                ON c.id_usuario = u.id_usuario
             WHERE u.id_usuario = ?
               AND u.estado = 'ACTIVO'
               AND r.estado = 'ACTIVO'
            """;
    private static final String SQL_ID_POR_USERNAME =
            "SELECT id_usuario FROM USUARIO WHERE username = ?";

    private static final String SQL_RESERVAR_SALT =
            "SELECT salt FROM USUARIO_CREDENCIAL WHERE id_usuario = ? FOR UPDATE";

    /** Algoritmo, iteraciones y salt con que se calculo la huella guardada. */
    public record ParametrosClave(String algoritmo, int iteraciones, String salt) {
    }

    /** Resultado de SP_VALIDAR_ACCESO: 0 correcto, 1 incorrecta, 2 incorrecta y bloqueada. */
    public record ResultadoAcceso(Long idUsuario, int resultado) {
    }

    /** Perfil vigente y las marcas que permiten invalidar sesiones antiguas. */
    public record PerfilAcceso(UsuarioSesion usuario, LocalDateTime fechaCambioClave,
            LocalDateTime ultimoAcceso) {
    }

    public void establecerClave(Connection connection, long idUsuario, String algoritmo,
            int iteraciones, String salt, String huella) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_ESTABLECER_CLAVE)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idUsuario);
            statement.setString(2, algoritmo);
            statement.setInt(3, iteraciones);
            statement.setString(4, salt);
            statement.setString(5, huella);
            statement.execute();
        }
    }

    public ParametrosClave obtenerParametros(Connection connection, String username)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_OBTENER_PARAMETROS)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, username);
            statement.registerOutParameter(2, Types.VARCHAR);
            statement.registerOutParameter(3, Types.NUMERIC);
            statement.registerOutParameter(4, Types.VARCHAR);
            statement.execute();
            return new ParametrosClave(statement.getString(2), statement.getInt(3),
                    statement.getString(4));
        }
    }

    public ResultadoAcceso validarAcceso(Connection connection, String username,
            ParametrosClave parametros, String huella) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_VALIDAR_ACCESO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, username);
            statement.setString(2, parametros.algoritmo());
            statement.setInt(3, parametros.iteraciones());
            statement.setString(4, parametros.salt());
            statement.setString(5, huella);
            statement.registerOutParameter(6, Types.NUMERIC);
            statement.registerOutParameter(7, Types.NUMERIC);
            statement.execute();
            long idUsuario = statement.getLong(6);
            Long id = statement.wasNull() ? null : idUsuario;
            int resultado = statement.getInt(7);
            if (statement.wasNull()) {
                throw new SQLException("PKG_SEGURIDAD.SP_VALIDAR_ACCESO no devolvió un resultado");
            }
            return new ResultadoAcceso(id, resultado);
        }
    }

    /** Vacio si el usuario no existe, esta inactivo, su rol esta inactivo o no tiene clave. */
    public Optional<PerfilAcceso> buscarPerfil(Connection connection, long idUsuario)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_PERFIL)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idUsuario);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                long idSucursal = resultSet.getLong("id_sucursal");
                Long sucursal = resultSet.wasNull() ? null : idSucursal;
                UsuarioSesion usuario = new UsuarioSesion(
                        resultSet.getLong("id_usuario"),
                        resultSet.getString("username"),
                        resultSet.getString("nombre"),
                        resultSet.getString("rol"),
                        sucursal);
                return Optional.of(new PerfilAcceso(usuario,
                        aFecha(resultSet.getTimestamp("fecha_cambio")),
                        aFecha(resultSet.getTimestamp("ultimo_acceso"))));
            }
        }
    }

    /**
     * Reserva la credencial hasta el fin de la transaccion y devuelve su salt
     * actual; vacio si el usuario no tiene clave.
     */
    public Optional<String> reservarSalt(Connection connection, long idUsuario)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_RESERVAR_SALT)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idUsuario);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(resultSet.getString("salt"))
                        : Optional.empty();
            }
        }
    }

    public Optional<Long> buscarIdPorUsername(Connection connection, String username)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_ID_POR_USERNAME)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getLong(1)) : Optional.empty();
            }
        }
    }

    private static LocalDateTime aFecha(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
