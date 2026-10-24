package gt.edu.umg.sancarlos.config;

import java.util.Map;

/**
 * Configuración de acceso a Oracle obtenida desde variables de entorno.
 */
public final class DatabaseConfig {

    private static final String DB_URL = "DB_URL";
    private static final String DB_USER = "DB_USER";
    private static final String DB_PASSWORD = "DB_PASSWORD";

    private final String url;
    private final String user;
    private final String password;

    private DatabaseConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DatabaseConfig fromEnvironment() {
        return from(System.getenv());
    }

    static DatabaseConfig from(Map<String, String> environment) {
        return new DatabaseConfig(
                required(environment, DB_URL),
                required(environment, DB_USER),
                required(environment, DB_PASSWORD)
        );
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + name
            );
        }
        return value;
    }

    public String url() {
        return url;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }
}
