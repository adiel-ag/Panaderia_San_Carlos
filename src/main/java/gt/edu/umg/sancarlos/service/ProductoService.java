package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.ProductoDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.model.Producto;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Casos de uso de consulta del catálogo de productos.
 */
public class ProductoService {

    private final ProductoDAO productoDAO;

    public ProductoService() {
        this.productoDAO = new ProductoDAO();
    }

    public List<Producto> listarProductos() {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return productoDAO.listar(connection);
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No fue posible listar los productos",
                    exception
            );
        }
    }

    public Optional<Producto> buscarProductoPorId(long idProducto) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return productoDAO.buscarPorId(connection, idProducto);
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No fue posible buscar el producto con ID " + idProducto,
                    exception
            );
        }
    }
}
