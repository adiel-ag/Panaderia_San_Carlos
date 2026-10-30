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
                   p.fecha_creacion
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
        producto.setFechaCreacion(
                resultSet.getTimestamp("fecha_creacion").toLocalDateTime()
        );
        return producto;
    }
}
