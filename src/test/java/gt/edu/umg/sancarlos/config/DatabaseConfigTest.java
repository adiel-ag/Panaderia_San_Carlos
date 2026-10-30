package gt.edu.umg.sancarlos.config;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @Test
    void createsConfigurationFromRequiredVariables() {
        Map<String, String> environment = Map.of(
                "DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                "DB_USER", "app_user",
                "DB_PASSWORD", "secret"
        );

        DatabaseConfig config = DatabaseConfig.from(environment);

        assertEquals("jdbc:oracle:thin:@//localhost:1521/FREEPDB1", config.url());
        assertEquals("app_user", config.user());
        assertEquals("secret", config.password());
        assertEquals(5, config.connectTimeoutSegundos());
        assertEquals(15, config.queryTimeoutSegundos());
        assertEquals(30, config.readTimeoutSegundos());
    }

    @Test
    void aceptaTimeoutsPersonalizadosConEspacios() {
        Map<String, String> environment = new HashMap<>(variablesRequeridas());
        environment.put("DB_CONNECT_TIMEOUT_SEGUNDOS", " 8 ");
        environment.put("DB_QUERY_TIMEOUT_SEGUNDOS", " 12 ");
        environment.put("DB_READ_TIMEOUT_SEGUNDOS", " 40 ");

        DatabaseConfig config = DatabaseConfig.from(environment);

        assertEquals(8, config.connectTimeoutSegundos());
        assertEquals(12, config.queryTimeoutSegundos());
        assertEquals(40, config.readTimeoutSegundos());
    }

    @Test
    void timeoutEnBlancoUsaValorPredeterminado() {
        Map<String, String> environment = new HashMap<>(variablesRequeridas());
        environment.put("DB_CONNECT_TIMEOUT_SEGUNDOS", " ");
        environment.put("DB_QUERY_TIMEOUT_SEGUNDOS", "\t");
        environment.put("DB_READ_TIMEOUT_SEGUNDOS", "");

        DatabaseConfig config = DatabaseConfig.from(environment);

        assertEquals(5, config.connectTimeoutSegundos());
        assertEquals(15, config.queryTimeoutSegundos());
        assertEquals(30, config.readTimeoutSegundos());
    }

    @Test
    void rechazaTimeoutNoNumericoCeroONegativo() {
        for (String variable : new String[]{"DB_CONNECT_TIMEOUT_SEGUNDOS",
                "DB_QUERY_TIMEOUT_SEGUNDOS", "DB_READ_TIMEOUT_SEGUNDOS"}) {
            for (String valor : new String[]{"abc", "0", "-2"}) {
                Map<String, String> environment = new HashMap<>(variablesRequeridas());
                environment.put(variable, valor);

                IllegalStateException exception = assertThrows(IllegalStateException.class,
                        () -> DatabaseConfig.from(environment));
                assertTrue(exception.getMessage().contains(variable));
                assertTrue(exception.getMessage().contains(valor));
                assertFalse(exception.getMessage().contains("secret"));
            }
        }
    }

    @Test
    void lecturaDebeSuperarConsulta() {
        for (String lectura : new String[]{"15", "14"}) {
            Map<String, String> environment = new HashMap<>(variablesRequeridas());
            environment.put("DB_READ_TIMEOUT_SEGUNDOS", lectura);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> DatabaseConfig.from(environment));
            assertTrue(exception.getMessage().contains("DB_READ_TIMEOUT_SEGUNDOS"));
            assertFalse(exception.getMessage().contains("secret"));
        }
    }

    @Test
    void timeoutDeConsultaNoRequiereCredenciales() {
        assertEquals(15, DatabaseConfig.queryTimeoutSegundos(Map.of()));
        assertEquals(7, DatabaseConfig.queryTimeoutSegundos(Map.of(
                "DB_QUERY_TIMEOUT_SEGUNDOS", "7")));
    }

    private static Map<String, String> variablesRequeridas() {
        return Map.of("DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                "DB_USER", "app_user", "DB_PASSWORD", "secret");
    }

    @Test
    void rejectsEachMissingRequiredVariable() {
        for (String variable : new String[]{"DB_URL", "DB_USER", "DB_PASSWORD"}) {
            Map<String, String> environment = new HashMap<>(Map.of(
                    "DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                    "DB_USER", "app_user",
                    "DB_PASSWORD", "secret"
            ));
            environment.remove(variable);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> DatabaseConfig.from(environment)
            );

            assertEquals("Missing required environment variable: " + variable,
                    exception.getMessage());
        }
    }

    @Test
    void rejectsBlankRequiredVariable() {
        Map<String, String> environment = Map.of(
                "DB_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1",
                "DB_USER", " ",
                "DB_PASSWORD", "secret"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DatabaseConfig.from(environment)
        );

        assertEquals("Missing required environment variable: DB_USER",
                exception.getMessage());
    }
}
