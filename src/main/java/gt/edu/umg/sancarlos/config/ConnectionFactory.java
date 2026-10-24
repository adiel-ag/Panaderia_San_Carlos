package gt.edu.umg.sancarlos.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Punto único para abrir conexiones JDBC con Oracle.
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
        return DriverManager.getConnection(
                config.url(),
                config.user(),
                config.password()
        );
    }
}

