package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/** Acceso JDBC a las operaciones expuestas por PKG_RECETA. */
public class RecetaDAO {

    private static final String FN_RECETA_ACTIVA =
            "{ ? = call PKG_RECETA.FN_RECETA_ACTIVA(?) }";

    /** Devuelve el ID de la receta activa; la conexión pertenece a la capa superior. */
    public long buscarIdRecetaActiva(Connection connection, long idProducto)
            throws SQLException {
        try (CallableStatement statement = connection.prepareCall(FN_RECETA_ACTIVA)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.registerOutParameter(1, Types.NUMERIC);
            statement.setLong(2, idProducto);
            statement.execute();

            long idReceta = statement.getLong(1);
            if (statement.wasNull()) {
                throw new SQLException("PKG_RECETA.FN_RECETA_ACTIVA no devolvió un ID");
            }
            return idReceta;
        }
    }
}
