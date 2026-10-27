package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.ProduccionDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.exception.ProduccionSolicitudInvalidaException;
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
        validarId(idUsuario, "usuario");
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

    /** Asocia un pedido confirmado a una producción planificada en su propia transacción. */
    public void asociarPedido(long idProduccion, long idPedido) {
        validarId(idProduccion, "producción");
        validarId(idPedido, "pedido");
        enTransaccion("No fue posible asociar el pedido a la producción", connection -> {
            produccionDAO.asociarPedido(connection, idProduccion, idPedido);
            return null;
        });
    }

    /** Genera el plan de una producción ya creada; Oracle calcula cantidades y recetas. */
    public void generarPlan(long idProduccion) {
        validarId(idProduccion, "producción");
        enTransaccion("No fue posible generar el plan de producción", connection -> {
            produccionDAO.generarPlan(connection, idProduccion);
            return null;
        });
    }

    /**
     * Consulta los renglones del plan que Oracle ya generó. Devuelve una lista vacía
     * si la producción existe pero aún no tiene plan.
     */
    public List<ProduccionDetalle> consultarDetalle(long idProduccion) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            if (!produccionDAO.existe(connection, idProduccion)) {
                throw new ProduccionNoEncontradaException(
                        "No existe una producción con ID " + idProduccion, null);
            }
            return produccionDAO.consultarDetalle(connection, idProduccion);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar el detalle de producción", exception);
        }
    }

    /**
     * Registra la cantidad producida de un producto mediante PKG_PRODUCCION.
     * El valor reemplaza la cantidad registrada anteriormente; no se acumula.
     */
    public void registrarProducido(long idProduccion, long idProducto, BigDecimal cantidad) {
        validarId(idProduccion, "producción");
        validarId(idProducto, "producto");
        if (cantidad == null) {
            throw new IllegalArgumentException("La cantidad producida es obligatoria");
        }
        if (cantidad.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad producida debe ser mayor que cero");
        }
        enTransaccion("No fue posible registrar la cantidad producida", connection -> {
            produccionDAO.registrarProducido(connection, idProduccion, idProducto, cantidad);
            return null;
        });
    }

    /** Finaliza la producción; Oracle controla inventario y estados relacionados. */
    public void finalizarProduccion(long idProduccion, long idUsuario) {
        validarId(idProduccion, "producción");
        validarId(idUsuario, "usuario");
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
                    throw traducirErrorOracle(mensajeError, sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException(mensajeError, exception);
        }
    }

    private static void validarId(long id, String nombre) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID de " + nombre + " debe ser positivo");
        }
    }

    /** Traduce errores de PKG_PRODUCCION y de los paquetes que invoca; no expone texto SQL. */
    static RuntimeException traducirErrorOracle(String mensajeError, SQLException exception) {
        int codigo = Math.abs(exception.getErrorCode());
        return switch (codigo) {
            case 20409 -> new ProduccionSolicitudInvalidaException(
                    "La cantidad producida debe ser mayor que cero", exception);
            case 1403, 20401, 20411 -> new ProduccionNoEncontradaException(
                    mensajeNoEncontrado(codigo), exception);
            case 20402, 20403, 20404, 20405, 20406, 20407, 20408, 20410, 20412, 20413,
                    20201, 20202, 20003, 20004 -> new ProduccionConflictoException(
                    mensajeConflicto(codigo), exception);
            default -> new DataAccessException(mensajeError, exception);
        };
    }

    private static String mensajeNoEncontrado(int codigo) {
        return switch (codigo) {
            case 20401 -> "Usuario inexistente o inactivo";
            case 20411 -> "El producto no pertenece a esta producción";
            default -> "La producción o un registro relacionado no existe";
        };
    }

    private static String mensajeConflicto(int codigo) {
        return switch (codigo) {
            case 20402 -> "Solo pueden agregarse pedidos a una producción planificada";
            case 20403 -> "Solo pueden producirse pedidos confirmados";
            case 20404 -> "El pedido ya está asociado a esta producción";
            case 20405 -> "La producción ya fue procesada";
            case 20406 -> "La producción no posee pedidos";
            case 20407 -> "El plan de producción ya fue generado";
            case 20408 -> "Existen pedidos que ya no están confirmados";
            case 20410, 20412 -> "La producción no se encuentra en proceso";
            case 20413 -> "La producción realizada es menor que la planificada";
            case 20201 -> "Un producto del plan no posee una receta activa";
            case 20202 -> "Un producto del plan posee más de una receta activa";
            case 20003 -> "Inventario insuficiente para finalizar la producción";
            default -> "Un producto de la producción no posee inventario registrado";
        };
    }

    @FunctionalInterface
    private interface TrabajoTransaccional<T> {
        T ejecutar(Connection connection) throws SQLException;
    }
}
