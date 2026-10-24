package gt.edu.umg.sancarlos.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionFactoryIT {

    private static final String CONNECTION_TEST_SQL =
            "SELECT 'CONEXION CORRECTA' AS mensaje FROM DUAL";

    @Test
    void connectsToOracleAndReadsExpectedMessage() throws SQLException {
        try (Connection connection = ConnectionFactory.getConnection();
             PreparedStatement statement = connection.prepareStatement(CONNECTION_TEST_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            assertTrue(resultSet.next(), "La consulta debe devolver una fila");
            assertEquals("CONEXION CORRECTA", resultSet.getString("mensaje"));
        }
    }
}
