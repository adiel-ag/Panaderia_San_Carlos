package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.model.Producto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Consultas JDBC para leer productos y sus descripciones relacionadas.
 */
public class ProductoDAO {

    private static final String SELECT_PRODUCTOS = """
            SELECT p.id_producto,
                   p.id_tipo_producto,
                   p.id_unidad,
                   p.nombre,
                   tp.nombre AS tipo_producto_nombre,
                   um.nombre AS unidad_medida_nombre,
                   um.abreviatura AS unidad_medida_abreviatura,
                   p.stock_minimo,
                   p.estado,
                   p.fecha_creacion,
                   CASE WHEN EXISTS (SELECT 1 FROM INVENTARIO i WHERE i.id_producto = p.id_producto)
                          OR EXISTS (SELECT 1 FROM MOVIMIENTO_INVENTARIO m WHERE m.id_producto = p.id_producto)
                          OR EXISTS (SELECT 1 FROM RECETA r WHERE r.id_producto = p.id_producto)
                          OR EXISTS (SELECT 1 FROM RECETA_DETALLE rd WHERE rd.id_insumo = p.id_producto)
                          OR EXISTS (SELECT 1 FROM PEDIDO_DETALLE pd WHERE pd.id_producto = p.id_producto)
                          OR EXISTS (SELECT 1 FROM PRODUCCION_DETALLE pr WHERE pr.id_producto = p.id_producto)
                        THEN 1 ELSE 0 END AS en_uso
              FROM PRODUCTO p
              JOIN TIPO_PRODUCTO tp
                ON tp.id_tipo_producto = p.id_tipo_producto
              JOIN UNIDAD_MEDIDA um
                ON um.id_unidad = p.id_unidad
            """;

    private static final String SQL_LISTAR = SELECT_PRODUCTOS
            + " ORDER BY p.nombre";

    private static final String SQL_BUSCAR_POR_ID = SELECT_PRODUCTOS
            + " WHERE p.id_producto = ?";

    public List<Producto> listar(Connection connection) throws SQLException {
        List<Producto> productos = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(SQL_LISTAR)) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    productos.add(mapear(resultSet));
                }
            }
        }

        return productos;
    }

    public Optional<Producto> buscarPorId(Connection connection, long idProducto)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_BUSCAR_POR_ID)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapear(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<Producto> bloquear(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_producto FROM PRODUCTO WHERE id_producto = ? FOR UPDATE")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
            }
        }
        return buscarPorId(connection, id);
    }

    public boolean existeTipo(Connection connection, long id) throws SQLException {
        return existe(connection, "SELECT 1 FROM TIPO_PRODUCTO WHERE id_tipo_producto = ?", id);
    }

    public boolean existeUnidad(Connection connection, long id) throws SQLException {
        return existe(connection, "SELECT 1 FROM UNIDAD_MEDIDA WHERE id_unidad = ?", id);
    }

    private static boolean existe(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    public long crear(Connection connection, String nombre, long tipo, long unidad,
            java.math.BigDecimal minimo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO PRODUCTO (nombre, id_tipo_producto, id_unidad, stock_minimo, estado) "
                + "VALUES (?, ?, ?, ?, 'ACTIVO')", new String[]{"ID_PRODUCTO"})) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, nombre);
            statement.setLong(2, tipo);
            statement.setLong(3, unidad);
            statement.setBigDecimal(4, minimo);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        throw new SQLException("No se obtuvo el ID del producto");
    }

    public void actualizar(Connection connection, long id, String nombre, long tipo, long unidad,
            java.math.BigDecimal minimo) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE PRODUCTO SET nombre = ?, id_tipo_producto = ?, id_unidad = ?, "
                + "stock_minimo = ? WHERE id_producto = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, nombre);
            statement.setLong(2, tipo);
            statement.setLong(3, unidad);
            statement.setBigDecimal(4, minimo);
            statement.setLong(5, id);
            statement.executeUpdate();
        }
    }

    public void cambiarEstado(Connection connection, long id, String estado) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE PRODUCTO SET estado = ? WHERE id_producto = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, estado);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private static Producto mapear(ResultSet resultSet) throws SQLException {
        Producto producto = new Producto();
        producto.setIdProducto(resultSet.getLong("id_producto"));
        producto.setIdTipoProducto(resultSet.getLong("id_tipo_producto"));
        producto.setIdUnidad(resultSet.getLong("id_unidad"));
        producto.setNombre(resultSet.getString("nombre"));
        producto.setNombreTipoProducto(resultSet.getString("tipo_producto_nombre"));
        producto.setNombreUnidadMedida(resultSet.getString("unidad_medida_nombre"));
        producto.setAbreviaturaUnidadMedida(
                resultSet.getString("unidad_medida_abreviatura")
        );
        producto.setStockMinimo(resultSet.getBigDecimal("stock_minimo"));

        producto.setEstado(resultSet.getString("estado"));
        producto.setEnUso(resultSet.getInt("en_uso") == 1);
        producto.setFechaCreacion(
                resultSet.getTimestamp("fecha_creacion").toLocalDateTime()
        );
        return producto;
    }
}
