package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.model.ProduccionDetalle;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

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
    private static final String SQL_EXISTE =
            "SELECT COUNT(*) FROM PRODUCCION WHERE id_produccion = ?";

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
