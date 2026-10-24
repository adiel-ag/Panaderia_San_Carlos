package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.ProduccionDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.model.ProduccionDetalle;
import java.sql.Connection;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.List;

/** Coordina operaciones transaccionales de producción y delega reglas a Oracle. */
public class ProduccionService {

    private final ProduccionDAO produccionDAO;

    public ProduccionService() {
        this(new ProduccionDAO());
    }

    ProduccionService(ProduccionDAO produccionDAO) {
        this.produccionDAO = produccionDAO;
    }

    /** Conserva la operación de creación individual disponible desde la fase anterior. */
    public long crearProduccion(long idUsuario, String observacion) {
        return enTransaccion("No fue posible crear la producción", connection ->
                produccionDAO.crear(connection, idUsuario, observacion));
    }

    /** Crea, asocia uno o más pedidos y genera el plan en una sola transacción. */
    public long crearYGenerarPlan(long idUsuario, String observacion, List<Long> idPedidos) {
        if (idPedidos == null || idPedidos.isEmpty()
                || idPedidos.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("Debe indicarse al menos un ID de pedido");
        }
        return enTransaccion("No fue posible crear el plan de producción", connection -> {
            long idProduccion = produccionDAO.crear(connection, idUsuario, observacion);
            for (Long idPedido : idPedidos) {
                produccionDAO.asociarPedido(connection, idProduccion, idPedido);
            }
            produccionDAO.generarPlan(connection, idProduccion);
            return idProduccion;
        });
    }

    /** Consulta los renglones del plan que Oracle ya generó. */
    public List<ProduccionDetalle> consultarDetalle(long idProduccion) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return produccionDAO.consultarDetalle(connection, idProduccion);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar el detalle de producción", exception);
        }
    }

    /** Registra una cantidad producida mediante PKG_PRODUCCION. */
    public void registrarProducido(long idProduccion, long idProducto, BigDecimal cantidad) {
        if (cantidad == null) {
            throw new IllegalArgumentException("La cantidad producida es obligatoria");
        }
        enTransaccion("No fue posible registrar la cantidad producida", connection -> {
            produccionDAO.registrarProducido(connection, idProduccion, idProducto, cantidad);
            return null;
        });
    }

    /** Finaliza la producción; Oracle controla inventario y estados relacionados. */
    public void finalizarProduccion(long idProduccion, long idUsuario) {
        enTransaccion("No fue posible finalizar la producción", connection -> {
            produccionDAO.finalizar(connection, idProduccion, idUsuario);
            return null;
        });
    }

    private <T> T enTransaccion(String mensajeError, TrabajoTransaccional<T> trabajo) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                if (exception instanceof SQLException sqlException) {
                    throw new DataAccessException(mensajeError, sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException(mensajeError, exception);
        }
    }

    @FunctionalInterface
    private interface TrabajoTransaccional<T> {
        T ejecutar(Connection connection) throws SQLException;
    }
}
