package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.PedidoDAO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDetalleDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.PedidoConflictoException;
import gt.edu.umg.sancarlos.exception.PedidoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.PedidoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.Pedido;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Coordina las transacciones de pedido y sus consultas. */
public class PedidoService {

    private final PedidoDAO pedidoDAO;

    public PedidoService() {
        this(new PedidoDAO());
    }

    PedidoService(PedidoDAO pedidoDAO) {
        this.pedidoDAO = pedidoDAO;
    }

    /** Ejecuta todas las operaciones del pedido sobre una única conexión. */
    public long crearPedido(CrearPedidoDTO solicitud) {
        validarSolicitud(solicitud);

        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long idPedido = pedidoDAO.crear(connection, solicitud);
                for (CrearPedidoDetalleDTO detalle : solicitud.getDetalles()) {
                    pedidoDAO.agregarDetalle(connection, idPedido, detalle);
                }
                pedidoDAO.confirmar(connection, idPedido);
                connection.commit();
                return idPedido;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) {
                    throw traducirErrorOracle(sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible completar la transacción del pedido", exception);
        }
    }

    public Optional<Pedido> buscarPedidoPorId(long idPedido) {
        if (idPedido <= 0) {
            throw new IllegalArgumentException("El ID del pedido debe ser positivo");
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            return pedidoDAO.buscarPorId(connection, idPedido);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar el pedido", exception);
        }
    }

    private static void validarSolicitud(CrearPedidoDTO solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud del pedido es obligatoria");
        }
        if (solicitud.getIdSucursal() <= 0 || solicitud.getIdUsuario() <= 0) {
            throw new IllegalArgumentException("Sucursal y usuario deben ser válidos");
        }
        if (solicitud.getFechaEntrega() == null) {
            throw new IllegalArgumentException("La fecha de entrega es obligatoria");
        }
        if (solicitud.getDetalles() == null || solicitud.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("El pedido debe incluir al menos un detalle");
        }
        if (solicitud.getDetalles().stream().anyMatch(detalle -> detalle == null
                || detalle.getIdProducto() <= 0
                || detalle.getCantidad() == null
                || detalle.getCantidad().signum() <= 0)) {
            throw new IllegalArgumentException("Cada detalle requiere producto y cantidad positiva");
        }
    }

    private static RuntimeException traducirErrorOracle(SQLException exception) {
        int codigo = Math.abs(exception.getErrorCode());
        return switch (codigo) {
            case 20301 -> new PedidoConflictoException("La sucursal está inactiva", exception);
            case 20302 -> new PedidoConflictoException(
                    "El usuario no pertenece a la sucursal indicada", exception);
            case 20303, 20305, 20307, 20312 -> new PedidoSolicitudInvalidaException(
                    mensajeSolicitud(codigo), exception);
            case 20304, 20310 -> new PedidoNoEncontradoException(
                    mensajeNoEncontrado(codigo), exception);
            case 20306, 20308, 20309, 20311, 20313, 20314 -> new PedidoConflictoException(
                    mensajeConflicto(codigo), exception);
            default -> new DataAccessException(
                    "No fue posible crear y confirmar el pedido", exception);
        };
    }

    private static String mensajeSolicitud(int codigo) {
        return switch (codigo) {
            case 20303 -> "La fecha de entrega no puede estar en el pasado";
            case 20305 -> "La cantidad debe ser mayor que cero";
            case 20307 -> "Solo se pueden solicitar productos finales";
            default -> "No se puede confirmar un pedido sin productos";
        };
    }

    private static String mensajeNoEncontrado(int codigo) {
        return codigo == 20304
                ? "Usuario o sucursal no encontrados"
                : "Pedido o producto no encontrado";
    }

    private static String mensajeConflicto(int codigo) {
        return switch (codigo) {
            case 20306 -> "Solo se pueden modificar pedidos pendientes";
            case 20308 -> "El producto se encuentra inactivo";
            case 20309 -> "El producto ya fue agregado al pedido";
            case 20311 -> "Solo se pueden confirmar pedidos pendientes";
            case 20313 -> "El pedido ya no puede cancelarse";
            default -> "El pedido ya está asociado a una producción";
        };
    }

    private static void rollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }
}
