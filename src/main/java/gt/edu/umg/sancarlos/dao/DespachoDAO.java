package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.model.Despacho;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/** Acceso a PKG_DESPACHO y lectura del despacho registrado. */
public class DespachoDAO {

    private static final String SP_DESPACHAR_PEDIDO =
            "{ call PKG_DESPACHO.SP_DESPACHAR_PEDIDO(?, ?, ?) }";
    private static final String SQL_BUSCAR_POR_PEDIDO = """
            SELECT id_despacho,
                   id_pedido,
                   id_usuario,
                   fecha_despacho,
                   estado,
                   observacion
              FROM DESPACHO
             WHERE id_pedido = ?
            """;

    public void despacharPedido(Connection connection, long idPedido, long idUsuario,
            String observacion) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_DESPACHAR_PEDIDO)) {
            statement.setLong(1, idPedido);
            statement.setLong(2, idUsuario);
            statement.setString(3, observacion);
            statement.execute();
        }
    }

    /** Un pedido tiene a lo sumo un despacho (uq_despacho_pedido). */
    public Optional<Despacho> buscarPorPedido(Connection connection, long idPedido)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_BUSCAR_POR_PEDIDO)) {
            statement.setLong(1, idPedido);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Despacho despacho = new Despacho();
                despacho.setIdDespacho(resultSet.getLong("id_despacho"));
                despacho.setIdPedido(resultSet.getLong("id_pedido"));
                despacho.setIdUsuario(resultSet.getLong("id_usuario"));
                Timestamp fecha = resultSet.getTimestamp("fecha_despacho");
                despacho.setFechaDespacho(fecha == null ? null : fecha.toLocalDateTime());
                despacho.setEstado(resultSet.getString("estado"));
                despacho.setObservacion(resultSet.getString("observacion"));
                return Optional.of(despacho);
            }
        }
    }
}
