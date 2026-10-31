package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.BloqueoSucursal;
import gt.edu.umg.sancarlos.dao.PedidoDAO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDetalleDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.PedidoResumenDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.PedidoConflictoException;
import gt.edu.umg.sancarlos.exception.PedidoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.PedidoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.time.LocalDate;
import java.util.Set;
import gt.edu.umg.sancarlos.resource.Paginacion;

/** Coordina las transacciones de pedido y sus consultas. */
public class PedidoService {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "CONFIRMADO",
            "EN_PRODUCCION", "LISTO_DESPACHO", "DESPACHADO", "CANCELADO");

    private static final String ROL_SUCURSAL = "SUCURSAL";
    private static final int SIN_DATOS = 1403;

    private final PedidoDAO pedidoDAO;
    private final ProveedorConexion conexiones;

    @FunctionalInterface
    interface ProveedorConexion {
        Connection abrir() throws SQLException;
    }

    public PedidoService() {
        this(new PedidoDAO());
    }

    PedidoService(PedidoDAO pedidoDAO) {
        this(pedidoDAO, ConnectionFactory::getConnection);
    }

    PedidoService(PedidoDAO pedidoDAO, ProveedorConexion conexiones) {
        this.pedidoDAO = pedidoDAO;
        this.conexiones = conexiones;
    }

    /** Ejecuta todas las operaciones del pedido sobre una única conexión. */
    public long crearPedido(CrearPedidoDTO solicitud) {
        validarSolicitud(solicitud);

        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Un pedido nuevo no debe cruzarse con desactivar su sucursal.
                BloqueoSucursal.bloquear(connection, solicitud.getIdSucursal());
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

    /**
     * Cancela el pedido con PKG_PEDIDO y devuelve el pedido actualizado. SUCURSAL
     * solo cancela pedidos de su sucursal; los ajenos responden como inexistentes.
     */
    public Pedido cancelarPedido(long idPedido, UsuarioSesion usuario) {
        if (idPedido <= 0) {
            throw new IllegalArgumentException("El ID del pedido debe ser positivo");
        }
        if (usuario == null) {
            throw new IllegalArgumentException("El usuario de la sesión es obligatorio");
        }
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                Pedido pedido = pedidoDAO.buscarPorId(connection, idPedido)
                        .filter(encontrado -> puedeVer(usuario, encontrado.getIdSucursal()))
                        .orElseThrow(() -> noEncontrado(idPedido, null));
                pedidoDAO.cancelar(connection, pedido.getIdPedido());
                Pedido cancelado = pedidoDAO.buscarPorId(connection, idPedido)
                        .orElseThrow(() -> noEncontrado(idPedido, null));
                connection.commit();
                return cancelado;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) {
                    if (Math.abs(sqlException.getErrorCode()) == SIN_DATOS) {
                        throw noEncontrado(idPedido, sqlException);
                    }
                    throw traducirErrorOracle(sqlException, "No fue posible cancelar el pedido");
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible cancelar el pedido", exception);
        }
    }

    private static boolean puedeVer(UsuarioSesion usuario, long idSucursalDelPedido) {
        return !ROL_SUCURSAL.equals(usuario.getRol())
                || (usuario.getIdSucursal() != null
                        && Objects.equals(usuario.getIdSucursal(), idSucursalDelPedido));
    }

    private static PedidoNoEncontradoException noEncontrado(long idPedido, Throwable causa) {
        return new PedidoNoEncontradoException("No existe un pedido con ID " + idPedido, causa);
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

    /** Lista cabeceras sin cargar los renglones de detalle. */
    public Pagina<PedidoResumenDTO> listar(int pagina, int tamano, String estado,
            Long idSucursal, LocalDate entregaDesde, LocalDate entregaHasta) {
        if (!Paginacion.valida(pagina, tamano)) {
            throw new IllegalArgumentException("La página o el tamaño no son válidos.");
        }
        validarEstado(estado);
        if (idSucursal != null && idSucursal <= 0) {
            throw new IllegalArgumentException("El ID de sucursal debe ser positivo.");
        }
        if (entregaDesde != null && entregaHasta != null
                && entregaDesde.isAfter(entregaHasta)) {
            throw new IllegalArgumentException("entregaDesde debe ser anterior o igual a entregaHasta.");
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            return pedidoDAO.listar(connection, pagina, tamano, estado, idSucursal,
                    entregaDesde, entregaHasta);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible listar los pedidos", exception);
        }
    }

    public static void validarEstado(String estado) {
        if (estado != null && !ESTADOS.contains(estado)) {
            throw new IllegalArgumentException("El estado del pedido no es válido.");
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
        return traducirErrorOracle(exception, "No fue posible crear y confirmar el pedido");
    }

    private static RuntimeException traducirErrorOracle(SQLException exception,
            String mensajeError) {
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
            default -> new DataAccessException(mensajeError, exception);
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
