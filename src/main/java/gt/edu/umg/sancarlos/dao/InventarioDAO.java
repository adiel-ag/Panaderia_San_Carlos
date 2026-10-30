package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/** Acceso JDBC a las operaciones expuestas por PKG_INVENTARIO. */
public class InventarioDAO {

    private static final String FN_EXISTENCIA =
            "{ ? = call PKG_INVENTARIO.FN_EXISTENCIA(?) }";
    private static final String SP_ENTRADA =
            "{ call PKG_INVENTARIO.SP_ENTRADA(?, ?, ?, ?, ?, ?) }";
    private static final String SP_SALIDA =
            "{ call PKG_INVENTARIO.SP_SALIDA(?, ?, ?, ?, ?, ?) }";

    /** Devuelve la existencia calculada por Oracle; 0 si el producto no tiene inventario. */
    public BigDecimal consultarExistencia(Connection connection, long idProducto)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(FN_EXISTENCIA)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.registerOutParameter(1, Types.NUMERIC);
            statement.setLong(2, idProducto);
            statement.execute();
            BigDecimal existencia = statement.getBigDecimal(1);
            if (existencia == null) {
                throw new SQLException("PKG_INVENTARIO.FN_EXISTENCIA no devolvió una existencia");
            }
            return existencia;
        }
    }

    public void registrarEntrada(Connection connection, long idProducto, BigDecimal cantidad,
            long idUsuario, String origen, String observacion) throws SQLException {
        registrarMovimiento(connection, SP_ENTRADA, idProducto, cantidad,
                idUsuario, origen, observacion);
    }

    public void registrarSalida(Connection connection, long idProducto, BigDecimal cantidad,
            long idUsuario, String origen, String observacion) throws SQLException {
        registrarMovimiento(connection, SP_SALIDA, idProducto, cantidad,
                idUsuario, origen, observacion);
    }

    private static void registrarMovimiento(Connection connection, String llamada,
            long idProducto, BigDecimal cantidad, long idUsuario, String origen,
            String observacion) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(llamada)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            statement.setBigDecimal(2, cantidad);
            statement.setLong(3, idUsuario);
            statement.setString(4, origen);
            statement.setNull(5, Types.NUMERIC);
            statement.setString(6, observacion);
            statement.execute();
        }
    }
}
