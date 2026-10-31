package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.InventarioDAO;
import gt.edu.umg.sancarlos.dao.ProductoDAO;
import gt.edu.umg.sancarlos.dto.ExistenciaDTO;
import gt.edu.umg.sancarlos.dto.ExistenciaResumenDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.MovimientoDTO;
import gt.edu.umg.sancarlos.resource.Paginacion;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.InventarioConflictoException;
import gt.edu.umg.sancarlos.exception.InventarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.InventarioSolicitudInvalidaException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

/** Coordina consultas y movimientos manuales de inventario; Oracle calcula la existencia. */
public class InventarioService {

    /** Lista productos activos con existencia nula si no existe fila de inventario. */
    public Pagina<ExistenciaResumenDTO> listarExistencias(int pagina, int tamano,
            String tipo, String nombre, Boolean bajoMinimo) {
        if (!Paginacion.valida(pagina, tamano)) {
            throw new IllegalArgumentException("La página o el tamaño no son válidos.");
        }
        validarFiltros(tipo, nombre);
        String filtroNombre = nombre == null || nombre.isBlank()
                ? null : escaparLike(nombre.trim());
        try (Connection connection = ConnectionFactory.getConnection()) {
            return inventarioDAO.listarExistencias(connection, pagina, tamano, tipo,
                    filtroNombre, bajoMinimo);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible listar las existencias", exception);
        }
    }

    public static void validarFiltros(String tipo, String nombre) {
        if (tipo != null && !"INSUMO".equals(tipo) && !"PRODUCTO_FINAL".equals(tipo)) {
            throw new IllegalArgumentException("El tipo de producto no es válido.");
        }
        if (nombre != null && nombre.length() > 100) {
            throw new IllegalArgumentException("El nombre no puede superar 100 caracteres.");
        }
    }

    static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** Los movimientos manuales se registran como ajustes; PRODUCCION y DESPACHO son de sus paquetes. */
    static final String ORIGEN_AJUSTE = "AJUSTE";
    private static final int LONGITUD_MAXIMA_OBSERVACION = 500;

    private final InventarioDAO inventarioDAO;
    private final ProductoDAO productoDAO;

    public InventarioService() {
        this(new InventarioDAO(), new ProductoDAO());
    }

    InventarioService(InventarioDAO inventarioDAO, ProductoDAO productoDAO) {
        this.inventarioDAO = inventarioDAO;
        this.productoDAO = productoDAO;
    }

    /** Consulta la existencia; un producto sin fila de inventario tiene existencia 0. */
    public ExistenciaDTO consultarExistencia(long idProducto) {
        validarId(idProducto, "producto");
        try (Connection connection = ConnectionFactory.getConnection()) {
            validarProductoExiste(connection, idProducto);
            return new ExistenciaDTO(idProducto,
                    inventarioDAO.consultarExistencia(connection, idProducto));
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar la existencia", exception);
        }
    }

    public Pagina<MovimientoDTO> listarMovimientos(long idProducto, int pagina, int tamano) {
        validarId(idProducto, "producto");
        if (!Paginacion.valida(pagina, tamano)) {
            throw new IllegalArgumentException("La página o el tamaño no son válidos.");
        }
        try (Connection connection = ConnectionFactory.getConnection()) {
            validarProductoExiste(connection, idProducto);
            return inventarioDAO.listarMovimientos(connection, idProducto, pagina, tamano);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible listar los movimientos", exception);
        }
    }

    /** Suma la cantidad mediante PKG_INVENTARIO.SP_ENTRADA y devuelve la nueva existencia. */
    public ExistenciaDTO registrarEntrada(long idProducto, long idUsuario,
            BigDecimal cantidad, String observacion) {
        validarMovimiento(idProducto, idUsuario, cantidad, observacion);
        return enTransaccion("No fue posible registrar la entrada de inventario", idProducto,
                connection -> inventarioDAO.registrarEntrada(connection, idProducto, cantidad,
                        idUsuario, ORIGEN_AJUSTE, observacion));
    }

    /** Descuenta la cantidad mediante PKG_INVENTARIO.SP_SALIDA y devuelve la nueva existencia. */
    public ExistenciaDTO registrarSalida(long idProducto, long idUsuario,
            BigDecimal cantidad, String observacion) {
        validarMovimiento(idProducto, idUsuario, cantidad, observacion);
        return enTransaccion("No fue posible registrar la salida de inventario", idProducto,
                connection -> inventarioDAO.registrarSalida(connection, idProducto, cantidad,
                        idUsuario, ORIGEN_AJUSTE, observacion));
    }

    private ExistenciaDTO enTransaccion(String mensajeError, long idProducto,
            Movimiento movimiento) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try {
                validarProductoExiste(connection, idProducto);
                movimiento.ejecutar(connection);
                BigDecimal existencia = inventarioDAO.consultarExistencia(connection, idProducto);
                connection.commit();
                return new ExistenciaDTO(idProducto, existencia);
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

    private void validarProductoExiste(Connection connection, long idProducto)
            throws SQLException {
        if (productoDAO.buscarPorId(connection, idProducto).isEmpty()) {
            throw new InventarioNoEncontradoException(
                    "No existe un producto con ID " + idProducto, null);
        }
    }

    private static void validarMovimiento(long idProducto, long idUsuario,
            BigDecimal cantidad, String observacion) {
        validarId(idProducto, "producto");
        validarId(idUsuario, "usuario");
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (observacion != null && observacion.length() > LONGITUD_MAXIMA_OBSERVACION) {
            throw new IllegalArgumentException(
                    "La observación no puede superar " + LONGITUD_MAXIMA_OBSERVACION + " caracteres");
        }
    }

    private static void validarId(long id, String nombre) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID de " + nombre + " debe ser positivo");
        }
    }

    /** Traduce errores de PKG_INVENTARIO; no expone texto SQL. */
    static RuntimeException traducirErrorOracle(String mensajeError, SQLException exception) {
        return switch (Math.abs(exception.getErrorCode())) {
            case 20001, 20002 -> new InventarioSolicitudInvalidaException(
                    "La cantidad debe ser mayor que cero", exception);
            case 20003 -> new InventarioConflictoException("Inventario insuficiente", exception);
            case 20004 -> new InventarioConflictoException(
                    "El producto no posee inventario registrado", exception);
            // ORA-02291: el producto ya se validó, por lo que la FK faltante es la del usuario.
            case 2291 -> new InventarioNoEncontradoException("Usuario inexistente", exception);
            default -> new DataAccessException(mensajeError, exception);
        };
    }

    @FunctionalInterface
    private interface Movimiento {
        void ejecutar(Connection connection) throws SQLException;
    }
}
