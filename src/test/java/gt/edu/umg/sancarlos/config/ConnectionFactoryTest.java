package gt.edu.umg.sancarlos.config;

import java.util.Map;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionFactoryTest {

    @Test
    void propiedadesIncluyenUsuarioClaveYTimeoutsEnMilisegundos() {
        DatabaseConfig config = DatabaseConfig.from(Map.of(
                "DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                "DB_USER", "usuario-prueba",
                "DB_PASSWORD", "clave-prueba",
                "DB_CONNECT_TIMEOUT_SEGUNDOS", "8",
                "DB_QUERY_TIMEOUT_SEGUNDOS", "12",
                "DB_READ_TIMEOUT_SEGUNDOS", "40"));

        Properties properties = ConnectionFactory.propiedades(config);

        assertEquals("usuario-prueba", properties.getProperty("user"));
        assertEquals("clave-prueba", properties.getProperty("password"));
        assertEquals("8000", properties.getProperty(
                OracleConnection.CONNECTION_PROPERTY_THIN_NET_CONNECT_TIMEOUT));
        assertEquals("40000", properties.getProperty(
                OracleConnection.CONNECTION_PROPERTY_THIN_READ_TIMEOUT));
    }
}
