package gt.edu.umg.sancarlos.config;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
