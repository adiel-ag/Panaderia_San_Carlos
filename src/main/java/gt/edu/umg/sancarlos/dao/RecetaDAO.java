package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.IngredienteRecetaDTO;
import gt.edu.umg.sancarlos.dto.NuevoIngredienteDTO;
import gt.edu.umg.sancarlos.dto.RecetaDetalleDTO;
import gt.edu.umg.sancarlos.dto.RecetaResumenDTO;
import gt.edu.umg.sancarlos.dto.RecetaVersionDTO;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acceso JDBC a PKG_RECETA y a las tablas RECETA y RECETA_DETALLE. Las recetas
 * nunca se modifican ni se borran: PRODUCCION_DETALLE apunta a la version usada.
 */
public class RecetaDAO {

    private static final String FN_RECETA_ACTIVA =
            "{ ? = call PKG_RECETA.FN_RECETA_ACTIVA(?) }";

    private static final String SQL_LISTAR_RESUMEN = """
            SELECT p.id_producto, p.nombre, um.abreviatura,
                   NVL(v.recetas_vigentes, 0) AS recetas_vigentes,
                   r.id_receta, r.version, r.rendimiento, r.fecha_inicio,
                   (SELECT COUNT(*) FROM RECETA_DETALLE d
                     WHERE d.id_receta = r.id_receta) AS cantidad_ingredientes
              FROM PRODUCTO p
              JOIN TIPO_PRODUCTO tp ON tp.id_tipo_producto = p.id_tipo_producto
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = p.id_unidad
              LEFT JOIN (SELECT rv.id_producto, COUNT(*) AS recetas_vigentes,
                                MIN(rv.id_receta) AS id_receta
                           FROM RECETA rv
                          WHERE """ + vigente("rv") + """

                          GROUP BY rv.id_producto) v
                ON v.id_producto = p.id_producto
              LEFT JOIN RECETA r
                ON r.id_receta = v.id_receta AND v.recetas_vigentes = 1
             WHERE p.estado = 'ACTIVO'
               AND tp.nombre = 'PRODUCTO_FINAL'
             ORDER BY p.nombre, p.id_producto
            """;

    private static final String SQL_BUSCAR_RECETA = """
            SELECT r.id_receta, r.id_producto, p.nombre, um.abreviatura, r.version,
                   r.rendimiento, r.estado, r.fecha_inicio, r.fecha_fin,
                   CASE WHEN """ + vigente("r") + """
             THEN 1 ELSE 0 END AS vigente
              FROM RECETA r
              JOIN PRODUCTO p ON p.id_producto = r.id_producto
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = p.id_unidad
             WHERE r.id_receta = ?
            """;

    private static final String SQL_INGREDIENTES = """
            SELECT d.id_insumo, p.nombre, um.abreviatura, d.cantidad
              FROM RECETA_DETALLE d
              JOIN PRODUCTO p ON p.id_producto = d.id_insumo
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = p.id_unidad
             WHERE d.id_receta = ?
             ORDER BY p.nombre, d.id_insumo
            """;

    private static final String SQL_VERSIONES = """
            SELECT r.id_receta, r.version, r.rendimiento, r.estado, r.fecha_inicio,
                   r.fecha_fin,
                   CASE WHEN """ + vigente("r") + """
             THEN 1 ELSE 0 END AS vigente,
                   (SELECT COUNT(*) FROM RECETA_DETALLE d
                     WHERE d.id_receta = r.id_receta) AS cantidad_ingredientes
              FROM RECETA r
             WHERE r.id_producto = ?
             ORDER BY r.version DESC
            """;

    private static final String SQL_PRODUCTO = """
            SELECT p.id_producto, p.nombre, tp.nombre AS tipo, p.estado
              FROM PRODUCTO p
              JOIN TIPO_PRODUCTO tp ON tp.id_tipo_producto = p.id_tipo_producto
            """;

    private static final String SQL_SIGUIENTE_VERSION =
            "SELECT NVL(MAX(version), 0) + 1 FROM RECETA WHERE id_producto = ?";

    private static final String SQL_DESACTIVAR = "UPDATE RECETA SET estado = 'INACTIVA', "
            + "fecha_fin = SYSDATE WHERE id_producto = ? AND estado = 'ACTIVA'";

    private static final String SQL_INSERTAR_RECETA = "INSERT INTO RECETA "
            + "(id_producto, version, rendimiento, estado, fecha_inicio) "
            + "VALUES (?, ?, ?, 'ACTIVA', SYSDATE)";

    private static final String SQL_INSERTAR_DETALLE =
            "INSERT INTO RECETA_DETALLE (id_receta, id_insumo, cantidad) VALUES (?, ?, ?)";

    /** Producto con lo necesario para decidir si lleva receta o si es insumo. */
    public record ProductoReceta(long idProducto, String nombre, String tipo, String estado) {
    }

    /**
     * Misma condicion que PKG_RECETA.FN_RECETA_ACTIVA. Lleva espacios a ambos
     * lados porque el bloque de texto previo pierde su espacio final.
     */
    public static String vigente(String alias) {
        return " (" + alias + ".estado = 'ACTIVA' AND " + alias + ".fecha_inicio <= SYSDATE AND ("
                + alias + ".fecha_fin IS NULL OR " + alias + ".fecha_fin >= TRUNC(SYSDATE))) ";
    }

    /** Devuelve el ID de la receta activa; la conexión pertenece a la capa superior. */
    public long buscarIdRecetaActiva(Connection connection, long idProducto)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(FN_RECETA_ACTIVA)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.registerOutParameter(1, Types.NUMERIC);
            statement.setLong(2, idProducto);
            statement.execute();

            long idReceta = statement.getLong(1);
            if (statement.wasNull()) {
                throw new SQLException("PKG_RECETA.FN_RECETA_ACTIVA no devolvió un ID");
            }
            return idReceta;
        }
    }

    /** Un renglon por producto final activo, con su receta vigente si es unica. */
    public List<RecetaResumenDTO> listarResumen(Connection connection) throws SQLException {
        List<RecetaResumenDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_LISTAR_RESUMEN)) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long idReceta = rows.getLong("id_receta");
                    boolean sinReceta = rows.wasNull();
                    int version = rows.getInt("version");
                    items.add(new RecetaResumenDTO(rows.getLong("id_producto"),
                            rows.getString("nombre"), rows.getString("abreviatura"),
                            sinReceta ? null : idReceta, sinReceta ? null : version,
                            rows.getBigDecimal("rendimiento"),
                            rows.getInt("cantidad_ingredientes"),
                            fecha(rows, "fecha_inicio"), rows.getInt("recetas_vigentes")));
                }
            }
        }
        return items;
    }

    public Optional<RecetaDetalleDTO> buscarDetalle(Connection connection, long idReceta)
            throws SQLException {
        long idProducto;
        String nombre;
        String unidad;
        int version;
        BigDecimal rendimiento;
        String estado;
        LocalDate fechaInicio;
        LocalDate fechaFin;
        boolean vigente;
        try (PreparedStatement statement = connection.prepareStatement(SQL_BUSCAR_RECETA)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idReceta);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                idProducto = rows.getLong("id_producto");
                nombre = rows.getString("nombre");
                unidad = rows.getString("abreviatura");
                version = rows.getInt("version");
                rendimiento = rows.getBigDecimal("rendimiento");
                estado = rows.getString("estado");
                fechaInicio = fecha(rows, "fecha_inicio");
                fechaFin = fecha(rows, "fecha_fin");
                vigente = rows.getInt("vigente") == 1;
            }
        }
        List<IngredienteRecetaDTO> ingredientes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_INGREDIENTES)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idReceta);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ingredientes.add(new IngredienteRecetaDTO(rows.getLong("id_insumo"),
                            rows.getString("nombre"), rows.getString("abreviatura"),
                            rows.getBigDecimal("cantidad")));
                }
            }
        }
        return Optional.of(new RecetaDetalleDTO(idReceta, idProducto, nombre, unidad, version,
                rendimiento, estado, fechaInicio, fechaFin, vigente, ingredientes));
    }

    /** Historial de versiones del producto, la mas reciente primero. */
    public List<RecetaVersionDTO> listarVersiones(Connection connection, long idProducto)
            throws SQLException {
        List<RecetaVersionDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_VERSIONES)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(new RecetaVersionDTO(rows.getLong("id_receta"),
                            rows.getInt("version"), rows.getBigDecimal("rendimiento"),
                            rows.getString("estado"), fecha(rows, "fecha_inicio"),
                            fecha(rows, "fecha_fin"), rows.getInt("vigente") == 1,
                            rows.getInt("cantidad_ingredientes")));
                }
            }
        }
        return items;
    }

    public Optional<ProductoReceta> buscarProducto(Connection connection, long idProducto)
            throws SQLException {
        return producto(connection, idProducto, false);
    }

    /** Bloquea la fila del producto hasta el commit para serializar las versiones. */
    public Optional<ProductoReceta> bloquearProducto(Connection connection, long idProducto)
            throws SQLException {
        return producto(connection, idProducto, true);
    }

    private Optional<ProductoReceta> producto(Connection connection, long idProducto,
            boolean bloquear) throws SQLException {
        String sql = SQL_PRODUCTO + " WHERE p.id_producto = ?"
                + (bloquear ? " FOR UPDATE OF p.id_producto" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapearProducto(rows)) : Optional.empty();
            }
        }
    }

    /** Busca los productos indicados; los IDs ausentes no aparecen en el mapa. */
    public Map<Long, ProductoReceta> buscarProductos(Connection connection,
            Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String marcas = String.join(", ", Collections.nCopies(ids.size(), "?"));
        Map<Long, ProductoReceta> productos = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SQL_PRODUCTO + " WHERE p.id_producto IN (" + marcas + ")")) {
            ConnectionFactory.aplicarTimeout(statement);
            int indice = 1;
            for (Long id : ids) {
                statement.setLong(indice++, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ProductoReceta producto = mapearProducto(rows);
                    productos.put(producto.idProducto(), producto);
                }
            }
        }
        return productos;
    }

    public int siguienteVersion(Connection connection, long idProducto) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_SIGUIENTE_VERSION)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    /** Cierra las versiones activas conservando el historial; devuelve cuantas cerro. */
    public int desactivarActivas(Connection connection, long idProducto) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_DESACTIVAR)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            return statement.executeUpdate();
        }
    }

    public long insertarReceta(Connection connection, long idProducto, int version,
            BigDecimal rendimiento) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SQL_INSERTAR_RECETA, new String[]{"ID_RECETA"})) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            statement.setInt(2, version);
            statement.setBigDecimal(3, rendimiento);
            statement.executeUpdate();
            try (ResultSet claves = statement.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("INSERT INTO RECETA no devolvió el ID");
                }
                return claves.getLong(1);
            }
        }
    }

    public void insertarIngredientes(Connection connection, long idReceta,
            List<NuevoIngredienteDTO> ingredientes) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSERTAR_DETALLE)) {
            ConnectionFactory.aplicarTimeout(statement);
            for (NuevoIngredienteDTO ingrediente : ingredientes) {
                statement.setLong(1, idReceta);
                statement.setLong(2, ingrediente.idInsumo());
                statement.setBigDecimal(3, ingrediente.cantidad());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static ProductoReceta mapearProducto(ResultSet rows) throws SQLException {
        return new ProductoReceta(rows.getLong("id_producto"), rows.getString("nombre"),
                rows.getString("tipo"), rows.getString("estado"));
    }

    private static LocalDate fecha(ResultSet rows, String columna) throws SQLException {
        Date valor = rows.getDate(columna);
        return valor == null ? null : valor.toLocalDate();
    }
}
