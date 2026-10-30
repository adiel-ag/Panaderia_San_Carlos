package gt.edu.umg.sancarlos.config;

import java.util.Map;

/**
 * Configuración de Oracle desde DB_URL, DB_USER y DB_PASSWORD.
 * DB_CONNECT_TIMEOUT_SEGUNDOS (5), DB_QUERY_TIMEOUT_SEGUNDOS (15) y
 * DB_READ_TIMEOUT_SEGUNDOS (30) son opcionales; lectura debe superar consulta.
 */
public final class DatabaseConfig {

    private static final String DB_URL = "DB_URL";
    private static final String DB_USER = "DB_USER";
    private static final String DB_PASSWORD = "DB_PASSWORD";
    private static final String DB_CONNECT_TIMEOUT_SEGUNDOS = "DB_CONNECT_TIMEOUT_SEGUNDOS";
    private static final String DB_QUERY_TIMEOUT_SEGUNDOS = "DB_QUERY_TIMEOUT_SEGUNDOS";
    private static final String DB_READ_TIMEOUT_SEGUNDOS = "DB_READ_TIMEOUT_SEGUNDOS";

    private final String url;
    private final String user;
    private final String password;
    private final int connectTimeoutSegundos;
    private final int queryTimeoutSegundos;
    private final int readTimeoutSegundos;

    private DatabaseConfig(String url, String user, String password, Timeouts timeouts) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.connectTimeoutSegundos = timeouts.connect();
        this.queryTimeoutSegundos = timeouts.query();
        this.readTimeoutSegundos = timeouts.read();
    }

    public static DatabaseConfig fromEnvironment() {
        return from(System.getenv());
    }

    static DatabaseConfig from(Map<String, String> environment) {
        return new DatabaseConfig(
                required(environment, DB_URL),
                required(environment, DB_USER),
                required(environment, DB_PASSWORD),
                timeouts(environment)
        );
    }

    /** Lee solo los tiempos; las consultas con conexiones de prueba no necesitan credenciales. */
    public static int queryTimeoutSegundosDesdeEntorno() {
        return queryTimeoutSegundos(System.getenv());
    }

    static int queryTimeoutSegundos(Map<String, String> environment) {
        return timeouts(environment).query();
    }

    private static Timeouts timeouts(Map<String, String> environment) {
        int connect = timeout(environment, DB_CONNECT_TIMEOUT_SEGUNDOS, 5);
        int query = timeout(environment, DB_QUERY_TIMEOUT_SEGUNDOS, 15);
        int read = timeout(environment, DB_READ_TIMEOUT_SEGUNDOS, 30);
        if (read <= query) {
            throw new IllegalStateException(DB_READ_TIMEOUT_SEGUNDOS + "=" + read
                    + " debe ser mayor que " + DB_QUERY_TIMEOUT_SEGUNDOS);
        }
        return new Timeouts(connect, query, read);
    }

    private static int timeout(Map<String, String> environment, String name, int defaultValue) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int seconds = Integer.parseInt(value.trim());
            if (seconds > 0) {
                return seconds;
            }
        } catch (NumberFormatException exception) {
            // Se informa solo el valor del timeout invalido.
        }
        throw new IllegalStateException("Variable de entorno de timeout inválida: "
                + name + "=" + value + " (debe ser un entero positivo)");
    }

    private record Timeouts(int connect, int query, int read) {
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

    public int connectTimeoutSegundos() {
        return connectTimeoutSegundos;
    }

    public int queryTimeoutSegundos() {
        return queryTimeoutSegundos;
    }

    public int readTimeoutSegundos() {
        return readTimeoutSegundos;
    }
}
