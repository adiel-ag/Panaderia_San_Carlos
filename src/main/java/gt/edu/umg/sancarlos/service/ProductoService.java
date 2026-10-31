package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.ProductoDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.dto.ProductoSolicitudDTO;
import gt.edu.umg.sancarlos.exception.CatalogoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.exception.CatalogoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.CatalogoConflictoException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Casos de uso de consulta del catálogo de productos.
 */
public class ProductoService {

    private final ProductoDAO productoDAO;
    private final ProveedorConexion conexiones;

    @FunctionalInterface
    interface ProveedorConexion { Connection abrir() throws SQLException; }

    public ProductoService() {
        this(new ProductoDAO(), ConnectionFactory::getConnection);
    }

    ProductoService(ProductoDAO productoDAO, ProveedorConexion conexiones) {
        this.productoDAO = productoDAO;
        this.conexiones = conexiones;
    }

    public List<Producto> listarProductos() {
        try (Connection connection = conexiones.abrir()) {
            return productoDAO.listar(connection);
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No fue posible listar los productos",
                    exception
            );
        }
    }

    public Optional<Producto> buscarProductoPorId(long idProducto) {
        try (Connection connection = conexiones.abrir()) {
            return productoDAO.buscarPorId(connection, idProducto);
        } catch (SQLException exception) {
            throw new DataAccessException(
                    "No fue posible buscar el producto con ID " + idProducto,
                    exception
            );
        }
    }

    public Producto crear(ProductoSolicitudDTO solicitud) {
        validar(solicitud);
        return transaccion(connection -> {
            validarReferencias(connection, solicitud);
            long id = productoDAO.crear(connection, solicitud.nombre().trim(),
                    solicitud.idTipoProducto(), solicitud.idUnidad(), solicitud.stockMinimo());
            return productoDAO.buscarPorId(connection, id).orElseThrow(SQLException::new);
        });
    }

    public Producto actualizar(long id, ProductoSolicitudDTO solicitud) {
        validarId(id);
        validar(solicitud);
        return transaccion(connection -> {
            Producto anterior = productoDAO.bloquear(connection, id).orElseThrow(() ->
                    new CatalogoNoEncontradoException("No existe un producto con ID " + id));
            validarReferencias(connection, solicitud);
            if (anterior.isEnUso() && (anterior.getIdTipoProducto() != solicitud.idTipoProducto()
                    || anterior.getIdUnidad() != solicitud.idUnidad())) {
                throw new CatalogoConflictoException(
                        "No se puede cambiar el tipo o la unidad de un producto que ya se usó");
            }
            productoDAO.actualizar(connection, id, solicitud.nombre().trim(),
                    solicitud.idTipoProducto(), solicitud.idUnidad(), solicitud.stockMinimo());
            return productoDAO.buscarPorId(connection, id).orElseThrow(SQLException::new);
        });
    }

    public Producto cambiarEstado(long id, String estado) {
        validarId(id);
        if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) {
            throw new CatalogoSolicitudInvalidaException("El estado debe ser ACTIVO o INACTIVO");
        }
        return transaccion(connection -> {
            productoDAO.bloquear(connection, id).orElseThrow(() ->
                    new CatalogoNoEncontradoException("No existe un producto con ID " + id));
            productoDAO.cambiarEstado(connection, id, estado);
            return productoDAO.buscarPorId(connection, id).orElseThrow(SQLException::new);
        });
    }

    static void validar(ProductoSolicitudDTO solicitud) {
        if (solicitud == null) throw new CatalogoSolicitudInvalidaException("Los datos del producto son obligatorios");
        if (solicitud.nombre() == null || solicitud.nombre().trim().isEmpty()
                || solicitud.nombre().trim().length() > 100) {
            throw new CatalogoSolicitudInvalidaException("El nombre debe tener entre 1 y 100 caracteres");
        }
        if (solicitud.idTipoProducto() == null || solicitud.idTipoProducto() <= 0
                || solicitud.idUnidad() == null || solicitud.idUnidad() <= 0) {
            throw new CatalogoSolicitudInvalidaException("El tipo y la unidad son obligatorios");
        }
        BigDecimal minimo = solicitud.stockMinimo();
        if (minimo == null || minimo.signum() < 0 || minimo.stripTrailingZeros().scale() > 3
                || minimo.compareTo(new BigDecimal("999999999.999")) > 0) {
            throw new CatalogoSolicitudInvalidaException(
                    "El stock mínimo debe estar entre 0 y 999999999.999, con máximo 3 decimales");
        }
    }

    private void validarReferencias(Connection connection, ProductoSolicitudDTO solicitud) throws SQLException {
        if (!productoDAO.existeTipo(connection, solicitud.idTipoProducto())) {
            throw new CatalogoSolicitudInvalidaException("El tipo de producto no existe");
        }
        if (!productoDAO.existeUnidad(connection, solicitud.idUnidad())) {
            throw new CatalogoSolicitudInvalidaException("La unidad de medida no existe");
        }
    }

    private static void validarId(long id) {
        if (id <= 0) throw new CatalogoSolicitudInvalidaException("El ID debe ser positivo");
    }

    private <T> T transaccion(Trabajo<T> trabajo) {
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException error) { exception.addSuppressed(error); }
                if (exception instanceof SQLException sql) {
                    if (Math.abs(sql.getErrorCode()) == 1) throw new CatalogoConflictoException(
                            "Ya existe un producto con ese nombre", sql);
                    throw new DataAccessException("No fue posible guardar el producto", sql);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible guardar el producto", exception);
        }
    }

    @FunctionalInterface
    private interface Trabajo<T> { T ejecutar(Connection connection) throws SQLException; }
}
