package gt.edu.umg.sancarlos.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import oracle.jdbc.OracleConnection;

/**
 * Abre conexiones Oracle con DB_CONNECT_TIMEOUT_SEGUNDOS (5) y
 * DB_READ_TIMEOUT_SEGUNDOS (30); DB_QUERY_TIMEOUT_SEGUNDOS (15) limita
 * cada sentencia. Lectura debe superar consulta.
 */
public final class ConnectionFactory {

    private ConnectionFactory() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("El driver JDBC de Oracle no está disponible.", exception);
        }
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        return DriverManager.getConnection(config.url(), propiedades(config));
    }

    static Properties propiedades(DatabaseConfig config) {
        Properties properties = new Properties();
        properties.setProperty("user", config.user());
        properties.setProperty("password", config.password());
        properties.setProperty(OracleConnection.CONNECTION_PROPERTY_THIN_NET_CONNECT_TIMEOUT,
                String.valueOf(TimeUnit.SECONDS.toMillis(config.connectTimeoutSegundos())));
        properties.setProperty(OracleConnection.CONNECTION_PROPERTY_THIN_READ_TIMEOUT,
                String.valueOf(TimeUnit.SECONDS.toMillis(config.readTimeoutSegundos())));
        return properties;
    }

    /** Limita la ejecucion de cada sentencia JDBC. */
    public static void aplicarTimeout(Statement statement) throws SQLException {
        statement.setQueryTimeout(DatabaseConfig.queryTimeoutSegundosDesdeEntorno());
    }
}

