package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.model.ProduccionDetalle;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.ProduccionResumenDTO;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Acceso a los procedimientos de PKG_PRODUCCION y lectura de su plan. */
public class ProduccionDAO {

    private static final String SP_CREAR =
            "{ call PKG_PRODUCCION.SP_CREAR(?, ?, ?) }";
    private static final String SP_ASOCIAR_PEDIDO =
            "{ call PKG_PRODUCCION.SP_ASOCIAR_PEDIDO(?, ?) }";
    private static final String SP_GENERAR_PLAN =
            "{ call PKG_PRODUCCION.SP_GENERAR_PLAN(?) }";
    private static final String SP_REGISTRAR_PRODUCIDO =
            "{ call PKG_PRODUCCION.SP_REGISTRAR_PRODUCIDO(?, ?, ?) }";
    private static final String SP_FINALIZAR =
            "{ call PKG_PRODUCCION.SP_FINALIZAR(?, ?) }";
    private static final String SQL_DETALLE = """
            SELECT id_produccion_detalle,
                   id_produccion,
                   id_producto,
                   id_receta,
                   cantidad_planificada,
                   cantidad_producida
              FROM PRODUCCION_DETALLE
             WHERE id_produccion = ?
             ORDER BY id_producto
            """;
    private static final String SQL_BLOQUEAR_ESTADO =
            "SELECT estado FROM PRODUCCION WHERE id_produccion = ? FOR UPDATE";
    private static final String SQL_LIBERAR_PEDIDOS =
            "DELETE FROM PRODUCCION_PEDIDO WHERE id_produccion = ?";
    private static final String SQL_CANCELAR =
            "UPDATE PRODUCCION SET estado = 'CANCELADA' WHERE id_produccion = ?";
    private static final String SQL_EXISTE =
            "SELECT COUNT(*) FROM PRODUCCION WHERE id_produccion = ?";
    private static final String SQL_RESUMEN_SELECT = """
            SELECT p.id_produccion, p.id_usuario, u.nombre AS nombre_usuario,
                   p.fecha_produccion, p.estado, p.observacion,
                   (SELECT COUNT(*) FROM PRODUCCION_PEDIDO pp
                     WHERE pp.id_produccion = p.id_produccion) AS cantidad_pedidos
            """;
    private static final String SQL_RESUMEN_BASE = """
              FROM PRODUCCION p
              JOIN USUARIO u ON u.id_usuario = p.id_usuario
            """;

    public Pagina<ProduccionResumenDTO> listar(Connection connection, int pagina, int tamano,
            String estado) throws SQLException {
        String filtro = " WHERE (? IS NULL OR p.estado = ?)";
        long total;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*)" + SQL_RESUMEN_BASE + filtro)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, estado);
            statement.setString(2, estado);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                total = rows.getLong(1);
            }
        }
        List<ProduccionResumenDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_RESUMEN_SELECT
                + SQL_RESUMEN_BASE + filtro
                + " ORDER BY p.fecha_produccion DESC, p.id_produccion DESC"
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setString(1, estado);
            statement.setString(2, estado);
            statement.setLong(3, ((long) pagina - 1) * tamano);
            statement.setInt(4, tamano);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(mapearResumen(rows));
            }
        }
        return new Pagina<>(items, total, pagina, tamano);
    }

    public Optional<ProduccionResumenDTO> buscarResumenPorId(Connection connection,
            long idProduccion) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_RESUMEN_SELECT
                + SQL_RESUMEN_BASE + " WHERE p.id_produccion = ?")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapearResumen(rows)) : Optional.empty();
            }
        }
    }

    private static ProduccionResumenDTO mapearResumen(ResultSet rows) throws SQLException {
        return new ProduccionResumenDTO(rows.getLong("id_produccion"), rows.getLong("id_usuario"),
                rows.getString("nombre_usuario"),
                rows.getTimestamp("fecha_produccion").toLocalDateTime(),
                rows.getString("estado"), rows.getString("observacion"),
                rows.getLong("cantidad_pedidos"));
    }

    /** Crea una producción y devuelve el identificador generado por Oracle. */
    public long crear(Connection connection, long idUsuario, String observacion)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_CREAR)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idUsuario);
            statement.setString(2, observacion);
            statement.registerOutParameter(3, Types.NUMERIC);
            statement.execute();
            long idProduccion = statement.getLong(3);
            if (statement.wasNull()) {
                throw new SQLException("PKG_PRODUCCION.SP_CREAR no devolvió el ID de producción");
            }
            return idProduccion;
        }
    }

    public void asociarPedido(Connection connection, long idProduccion, long idPedido)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_ASOCIAR_PEDIDO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            statement.setLong(2, idPedido);
            statement.execute();
        }
    }

    public void generarPlan(Connection connection, long idProduccion) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_GENERAR_PLAN)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            statement.execute();
        }
    }

    /**
     * Cancela una producción planificada y libera sus pedidos, que siguen
     * CONFIRMADO porque asociarlos no les cambió el estado. No hace commit.
     */
    public void cancelar(Connection connection, long idProduccion) throws SQLException {
        String estado;
        try (PreparedStatement statement = connection.prepareStatement(SQL_BLOQUEAR_ESTADO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new ProduccionNoEncontradaException(
                            "No existe una producción con ID " + idProduccion, null);
                }
                estado = resultSet.getString("estado");
            }
        }
        if (!"PLANIFICADA".equals(estado)) {
            throw new ProduccionConflictoException(
                    "Solo se puede cancelar una producción planificada", null);
        }
        actualizar(connection, SQL_LIBERAR_PEDIDOS, idProduccion);
        actualizar(connection, SQL_CANCELAR, idProduccion);
    }

    private static void actualizar(Connection connection, String sql, long idProduccion)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            statement.executeUpdate();
        }
    }

    /** Indica si existe una producción con el identificador indicado. */
    public boolean existe(Connection connection, long idProduccion) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_EXISTE)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        }
    }

    /** Lee los detalles que PKG_PRODUCCION generó; no recalcula cantidades. */
    public List<ProduccionDetalle> consultarDetalle(Connection connection, long idProduccion)
            throws SQLException {
        List<ProduccionDetalle> detalles = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_DETALLE)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ProduccionDetalle detalle = new ProduccionDetalle();
                    detalle.setIdProduccionDetalle(resultSet.getLong("id_produccion_detalle"));
                    detalle.setIdProduccion(resultSet.getLong("id_produccion"));
                    detalle.setIdProducto(resultSet.getLong("id_producto"));
                    detalle.setIdReceta(resultSet.getLong("id_receta"));
                    detalle.setCantidadPlanificada(resultSet.getBigDecimal("cantidad_planificada"));
                    detalle.setCantidadProducida(resultSet.getBigDecimal("cantidad_producida"));
                    detalles.add(detalle);
                }
            }
        }
        return detalles;
    }

    public void registrarProducido(Connection connection, long idProduccion,
            long idProducto, java.math.BigDecimal cantidad) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_REGISTRAR_PRODUCIDO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            statement.setLong(2, idProducto);
            statement.setBigDecimal(3, cantidad);
            statement.execute();
        }
    }

    public void finalizar(Connection connection, long idProduccion, long idUsuario)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_FINALIZAR)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            statement.setLong(2, idUsuario);
            statement.execute();
        }
    }
}
