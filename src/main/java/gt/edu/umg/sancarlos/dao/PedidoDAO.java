package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDetalleDTO;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.PedidoDetalle;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/** Acceso JDBC a las operaciones ofrecidas por PKG_PEDIDO y a su consulta. */
public class PedidoDAO {

    private static final String SP_CREAR_PEDIDO =
            "{ call PKG_PEDIDO.SP_CREAR_PEDIDO(?, ?, ?, ?, ?) }";
    private static final String SP_AGREGAR_DETALLE =
            "{ call PKG_PEDIDO.SP_AGREGAR_DETALLE(?, ?, ?) }";
    private static final String SP_CONFIRMAR_PEDIDO =
            "{ call PKG_PEDIDO.SP_CONFIRMAR_PEDIDO(?) }";
    private static final String SQL_BUSCAR_POR_ID = """
            SELECT p.id_pedido,
                   p.id_sucursal,
                   p.id_usuario,
                   p.fecha_entrega,
                   p.estado,
                   p.observacion,
                   pd.id_pedido_detalle,
                   pd.id_producto,
                   pr.nombre AS nombre_producto,
                   pd.cantidad
              FROM PEDIDO p
              LEFT JOIN PEDIDO_DETALLE pd
                ON pd.id_pedido = p.id_pedido
              LEFT JOIN PRODUCTO pr
                ON pr.id_producto = pd.id_producto
             WHERE p.id_pedido = ?
             ORDER BY pd.id_pedido_detalle
            """;

    /** Crea la cabecera y retorna el ID generado; no administra la conexión. */
    public long crear(Connection connection, CrearPedidoDTO pedido) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_CREAR_PEDIDO)) {
            statement.setLong(1, pedido.getIdSucursal());
            statement.setLong(2, pedido.getIdUsuario());
            statement.setDate(3, java.sql.Date.valueOf(pedido.getFechaEntrega()));
            statement.setString(4, pedido.getObservacion());
            statement.registerOutParameter(5, Types.NUMERIC);
            statement.execute();

            long idPedido = statement.getLong(5);
            if (statement.wasNull()) {
                throw new SQLException("PKG_PEDIDO.SP_CREAR_PEDIDO no devolvió el ID del pedido");
            }
            return idPedido;
        }
    }

    /** Agrega un detalle mediante el paquete Oracle; no usa INSERT directo. */
    public void agregarDetalle(Connection connection, long idPedido,
            CrearPedidoDetalleDTO detalle) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_AGREGAR_DETALLE)) {
            statement.setLong(1, idPedido);
            statement.setLong(2, detalle.getIdProducto());
            statement.setBigDecimal(3, detalle.getCantidad());
            statement.execute();
        }
    }

    /** Confirma el pedido mediante el paquete Oracle. */
    public void confirmar(Connection connection, long idPedido) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_CONFIRMAR_PEDIDO)) {
            statement.setLong(1, idPedido);
            statement.execute();
        }
    }

    /** Busca la cabecera y sus detalles en la conexión recibida. */
    public Optional<Pedido> buscarPorId(Connection connection, long idPedido)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_BUSCAR_POR_ID)) {
            statement.setLong(1, idPedido);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Pedido pedido = mapearPedido(resultSet);
                do {
                    long idDetalle = resultSet.getLong("id_pedido_detalle");
                    if (!resultSet.wasNull()) {
                        PedidoDetalle detalle = new PedidoDetalle();
                        detalle.setIdPedidoDetalle(idDetalle);
                        detalle.setIdPedido(pedido.getIdPedido());
                        detalle.setIdProducto(resultSet.getLong("id_producto"));
                        detalle.setNombreProducto(resultSet.getString("nombre_producto"));
                        detalle.setCantidad(resultSet.getBigDecimal("cantidad"));
                        pedido.getDetalles().add(detalle);
                    }
                } while (resultSet.next());
                return Optional.of(pedido);
            }
        }
    }

    private static Pedido mapearPedido(ResultSet resultSet) throws SQLException {
        Pedido pedido = new Pedido();
        pedido.setIdPedido(resultSet.getLong("id_pedido"));
        pedido.setIdSucursal(resultSet.getLong("id_sucursal"));
        pedido.setIdUsuario(resultSet.getLong("id_usuario"));
        pedido.setFechaEntrega(resultSet.getDate("fecha_entrega").toLocalDate());
        pedido.setEstado(resultSet.getString("estado"));
        pedido.setObservacion(resultSet.getString("observacion"));
        return pedido;
    }
}
