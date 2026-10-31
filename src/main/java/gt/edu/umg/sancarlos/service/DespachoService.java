package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.DespachoDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.DespachoConflictoException;
import gt.edu.umg.sancarlos.exception.DespachoNoEncontradoException;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Coordina el despacho de pedidos; PKG_DESPACHO controla estados e inventario. */
public class DespachoService {

    private static final int LONGITUD_MAXIMA_OBSERVACION = 500;

    private final DespachoDAO despachoDAO;
    private final ProveedorConexion conexiones;

    @FunctionalInterface
    interface ProveedorConexion { Connection abrir() throws SQLException; }

    public DespachoService() {
        this(new DespachoDAO());
    }

    DespachoService(DespachoDAO despachoDAO) {
        this(despachoDAO, ConnectionFactory::getConnection);
    }

    DespachoService(DespachoDAO despachoDAO, ProveedorConexion conexiones) {
        this.despachoDAO = despachoDAO;
        this.conexiones = conexiones;
    }

    public Despacho recibirPedido(long idPedido, UsuarioSesion usuario, String observacion) {
        validarId(idPedido, "pedido");
        if (usuario == null) throw new IllegalArgumentException("El usuario de la sesión es obligatorio");
        String detalle = observacion == null ? "" : observacion.trim();
        if (detalle.length() > LONGITUD_MAXIMA_OBSERVACION) {
            throw new IllegalArgumentException("La observación no puede superar 500 caracteres");
        }
        String texto = detalle.isBlank() ? "[Recibido conforme]" : "[Recibido: " + detalle + "]";
        String mensajeError = "No fue posible registrar la recepción";
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                DespachoDAO.DespachoBloqueado bloqueado = despachoDAO.bloquearPorPedido(
                        connection, idPedido).orElseThrow(() -> new DespachoNoEncontradoException(
                                "El pedido no tiene despacho", null));
                if ("SUCURSAL".equals(usuario.getRol()) && (usuario.getIdSucursal() == null
                        || usuario.getIdSucursal() != bloqueado.idSucursal())) {
                    throw new DespachoNoEncontradoException("El pedido no tiene despacho", null);
                }
                if ("ENTREGADO".equals(bloqueado.estado())) {
                    throw new DespachoConflictoException("Este despacho ya fue recibido", null);
                }
                if (!"DESPACHADO".equals(bloqueado.estado())) {
                    throw new DespachoConflictoException("Solo se pueden recibir despachos despachados", null);
                }
                despachoDAO.registrarRecepcion(connection, idPedido, texto);
                Despacho despacho = despachoDAO.buscarPorPedido(connection, idPedido)
                        .orElseThrow(() -> new DespachoNoEncontradoException(
                                "El pedido no tiene despacho", null));
                connection.commit();
                return despacho;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); }
                catch (SQLException rollbackException) { exception.addSuppressed(rollbackException); }
                if (exception instanceof SQLException sql) throw new DataAccessException(mensajeError, sql);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException(mensajeError, exception);
        }
    }

    /** Despacha el pedido y devuelve el despacho leído dentro de la misma transacción. */
    public Despacho despacharPedido(long idPedido, long idUsuario, String observacion) {
        validarId(idPedido, "pedido");
        validarId(idUsuario, "usuario");
        if (observacion != null && observacion.length() > LONGITUD_MAXIMA_OBSERVACION) {
            throw new IllegalArgumentException(
                    "La observación no puede superar " + LONGITUD_MAXIMA_OBSERVACION + " caracteres");
        }

        String mensajeError = "No fue posible despachar el pedido";
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                despachoDAO.despacharPedido(connection, idPedido, idUsuario, observacion);
                Despacho despacho = despachoDAO.buscarPorPedido(connection, idPedido)
                        .orElseThrow(() -> new SQLException(
                                "PKG_DESPACHO.SP_DESPACHAR_PEDIDO no registró el despacho"));
                connection.commit();
                return despacho;
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

    public Optional<Despacho> buscarPorPedido(long idPedido) {
        validarId(idPedido, "pedido");
        try (Connection connection = ConnectionFactory.getConnection()) {
            return despachoDAO.buscarPorPedido(connection, idPedido);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar el despacho", exception);
        }
    }

    private static void validarId(long id, String nombre) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID de " + nombre + " debe ser positivo");
        }
    }

    /** Traduce errores de PKG_DESPACHO y PKG_INVENTARIO; no expone texto SQL. */
    static RuntimeException traducirErrorOracle(String mensajeError, SQLException exception) {
        return switch (Math.abs(exception.getErrorCode())) {
            case 20501 -> new DespachoConflictoException(
                    "El pedido aún no está listo para despacho", exception);
            case 20502 -> new DespachoConflictoException(
                    "El pedido ya posee un despacho", exception);
            case 20003 -> new DespachoConflictoException(
                    "Inventario insuficiente para despachar el pedido", exception);
            case 20004 -> new DespachoConflictoException(
                    "Un producto del pedido no posee inventario registrado", exception);
            case 1403 -> new DespachoNoEncontradoException("Pedido inexistente", exception);
            case 2291 -> new DespachoNoEncontradoException("Usuario inexistente", exception);
            default -> new DataAccessException(mensajeError, exception);
        };
    }
}
