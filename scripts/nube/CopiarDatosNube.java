import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Copia los datos de las 17 tablas de la base local (XE) al esquema PANADERIA
 * de Oracle Cloud. Se ejecuta entre nube/01_crear_esquema_nube.sql y
 * nube/02_finalizar_nube.sql (las llaves foraneas de la nube estan desactivadas).
 *
 * Seguridad:
 *  - La conexion local es de solo lectura (setReadOnly + nunca escribe en ella).
 *  - En la nube exige usuario PANADERIA y se niega si alguna tabla ya tiene filas.
 *  - Todo se confirma en una sola transaccion: si algo falla, nada queda copiado.
 *  - Las contrasenas se leen de variables de entorno / .env y nunca se imprimen.
 *
 * Uso: java -cp ojdbc17.jar scripts/nube/CopiarDatosNube.java
 * Local: DB_URL, DB_USER, DB_PASSWORD (variables de entorno).
 * Nube:  PSC_CLOUD_URL, PSC_CLOUD_USER, PSC_CLOUD_PASSWORD (entorno o .env).
 */
public class CopiarDatosNube {

    static final String[] TABLAS = {
        "ROL", "SUCURSAL", "TIPO_PRODUCTO", "UNIDAD_MEDIDA", "USUARIO", "USUARIO_CREDENCIAL",
        "PRODUCTO", "RECETA", "RECETA_DETALLE", "PEDIDO", "PEDIDO_DETALLE", "PRODUCCION",
        "PRODUCCION_PEDIDO", "PRODUCCION_DETALLE", "INVENTARIO", "MOVIMIENTO_INVENTARIO", "DESPACHO"
    };

    public static void main(String[] args) throws Exception {
        Properties env = leerEnv();
        String nubeUsuario = valor(env, "PSC_CLOUD_USER", "PANADERIA");
        if (!"PANADERIA".equalsIgnoreCase(nubeUsuario)) {
            throw new IllegalStateException("El usuario de la nube debe ser PANADERIA.");
        }
        try (Connection local = conectar(valor(env, "DB_URL", null), valor(env, "DB_USER", null),
                    valor(env, "DB_PASSWORD", null));
             Connection nube = conectar(valor(env, "PSC_CLOUD_URL", null), nubeUsuario,
                    valor(env, "PSC_CLOUD_PASSWORD", null))) {
            local.setReadOnly(true);
            local.setAutoCommit(false);
            nube.setAutoCommit(false);
            verificarNube(nube);
            for (String tabla : TABLAS) {
                long filas = copiar(local, nube, tabla);
                System.out.printf("%-25s %d filas copiadas%n", tabla, filas);
            }
            nube.commit();
            local.rollback();
            System.out.println("=== Copia confirmada en la nube.");
        }
    }

    static void verificarNube(Connection nube) throws SQLException {
        try (Statement st = nube.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT USER, SYS_CONTEXT('USERENV','CLOUD_SERVICE') FROM dual")) {
            rs.next();
            if (!"PANADERIA".equals(rs.getString(1)) || rs.getString(2) == null) {
                throw new IllegalStateException("La conexion destino no es PANADERIA en Oracle Cloud.");
            }
        }
        for (String tabla : TABLAS) {
            try (Statement st = nube.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM \"" + tabla + "\"")) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    throw new IllegalStateException("La tabla " + tabla
                            + " de la nube ya tiene datos; ejecute nube/00 y nube/01 antes de copiar.");
                }
            }
        }
    }

    static long copiar(Connection local, Connection nube, String tabla) throws SQLException {
        List<String> columnas = new ArrayList<>();
        try (Statement st = nube.createStatement();
             ResultSet rs = st.executeQuery("SELECT column_name FROM user_tab_columns WHERE table_name = '"
                     + tabla + "' ORDER BY column_id")) {
            while (rs.next()) columnas.add(rs.getString(1));
        }
        String lista = "\"" + String.join("\",\"", columnas) + "\"";
        String marcas = String.join(",", columnas.stream().map(c -> "?").toList());
        long filas = 0;
        try (Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + lista + " FROM \"" + tabla + "\"");
             PreparedStatement ins = nube.prepareStatement(
                     "INSERT INTO \"" + tabla + "\" (" + lista + ") VALUES (" + marcas + ")")) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                for (int i = 1; i <= columnas.size(); i++) {
                    Object v = rs.getObject(i);
                    if (v == null) ins.setNull(i, md.getColumnType(i));
                    else ins.setObject(i, v);
                }
                ins.addBatch();
                if (++filas % 500 == 0) ins.executeBatch();
            }
            ins.executeBatch();
        }
        return filas;
    }

    static Connection conectar(String url, String usuario, String clave) throws SQLException {
        if (url == null || usuario == null || clave == null) {
            throw new IllegalStateException("Faltan datos de conexion (URL, usuario o contrasena).");
        }
        Properties p = new Properties();
        p.setProperty("user", usuario);
        p.setProperty("password", clave);
        return DriverManager.getConnection(url, p);
    }

    static String valor(Properties env, String clave, String porDefecto) {
        String v = System.getenv(clave);
        if (v == null || v.isBlank()) v = env.getProperty(clave);
        return v == null || v.isBlank() ? porDefecto : v.trim();
    }

    static Properties leerEnv() throws Exception {
        Properties p = new Properties();
        Path archivo = Path.of(".env");
        if (Files.exists(archivo)) {
            for (String linea : Files.readAllLines(archivo)) {
                int igual = linea.indexOf('=');
                if (!linea.startsWith("#") && igual > 0) {
                    p.setProperty(linea.substring(0, igual).trim(), linea.substring(igual + 1).trim());
                }
            }
        }
        return p;
    }
}
