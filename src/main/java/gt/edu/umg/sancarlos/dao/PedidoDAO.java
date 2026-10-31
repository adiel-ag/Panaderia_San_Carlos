package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDetalleDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.PedidoResumenDTO;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.PedidoDetalle;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

/** Acceso JDBC a las operaciones ofrecidas por PKG_PEDIDO y a su consulta. */
public class PedidoDAO {

    private static final String SP_CREAR_PEDIDO =
            "{ call PKG_PEDIDO.SP_CREAR_PEDIDO(?, ?, ?, ?, ?) }";
    private static final String SP_AGREGAR_DETALLE =
            "{ call PKG_PEDIDO.SP_AGREGAR_DETALLE(?, ?, ?) }";
    private static final String SP_CONFIRMAR_PEDIDO =
            "{ call PKG_PEDIDO.SP_CONFIRMAR_PEDIDO(?) }";
    private static final String SP_CANCELAR_PEDIDO =
            "{ call PKG_PEDIDO.SP_CANCELAR_PEDIDO(?) }";
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
    private static final String SQL_RESUMEN_SELECT = """
            SELECT p.id_pedido, p.id_sucursal, s.nombre AS nombre_sucursal,
                   p.id_usuario, u.nombre AS nombre_usuario, p.fecha_pedido,
                   p.fecha_entrega, p.estado, p.observacion,
                   (SELECT COUNT(*) FROM PEDIDO_DETALLE pd
                     WHERE pd.id_pedido = p.id_pedido) AS cantidad_productos
            """;
    private static final String SQL_RESUMEN_BASE = """
              FROM PEDIDO p
              JOIN SUCURSAL s ON s.id_sucursal = p.id_sucursal
              JOIN USUARIO u ON u.id_usuario = p.id_usuario
            """;
    private static final String SQL_FILTROS = """
             WHERE (? IS NULL OR p.estado = ?)
               AND (? IS NULL OR p.id_sucursal = ?)
               AND (? IS NULL OR p.fecha_entrega >= ?)
               AND (? IS NULL OR p.fecha_entrega < ? + 1)
            """;

    /** Pagina cabeceras y cuenta el total con los mismos filtros. */
    public Pagina<PedidoResumenDTO> listar(Connection connection, int pagina, int tamano,
            String estado, Long idSucursal, LocalDate desde, LocalDate hasta)
            throws SQLException {
        long total;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*)" + SQL_RESUMEN_BASE + SQL_FILTROS)) {
            ConnectionFactory.aplicarTimeout(statement);
            filtros(statement, estado, idSucursal, desde, hasta);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                total = rows.getLong(1);
            }
        }
        List<PedidoResumenDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_RESUMEN_SELECT
                + SQL_RESUMEN_BASE + SQL_FILTROS
                // La entrega mas proxima primero: la pantalla responde que hay que preparar.
                + " ORDER BY p.fecha_entrega, p.id_pedido"
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            ConnectionFactory.aplicarTimeout(statement);
            filtros(statement, estado, idSucursal, desde, hasta);
            statement.setLong(9, ((long) pagina - 1) * tamano);
            statement.setInt(10, tamano);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(mapearResumen(rows));
            }
        }
        return new Pagina<>(items, total, pagina, tamano);
    }

    /** Usa la misma proyeccion y conversion que el listado de pedidos. */
    public List<PedidoResumenDTO> listarDeProduccion(Connection connection, long idProduccion)
            throws SQLException {
        List<PedidoResumenDTO> pedidos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_RESUMEN_SELECT
                + SQL_RESUMEN_BASE
                + " JOIN PRODUCCION_PEDIDO pp ON pp.id_pedido = p.id_pedido"
                + " WHERE pp.id_produccion = ?"
                + " ORDER BY p.fecha_pedido DESC, p.id_pedido DESC")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProduccion);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) pedidos.add(mapearResumen(rows));
            }
        }
        return pedidos;
    }

    private static void filtros(PreparedStatement statement, String estado, Long idSucursal,
            LocalDate desde, LocalDate hasta) throws SQLException {
        statement.setString(1, estado);
        statement.setString(2, estado);
        if (idSucursal == null) {
            statement.setNull(3, Types.NUMERIC);
            statement.setNull(4, Types.NUMERIC);
        } else {
            statement.setLong(3, idSucursal);
            statement.setLong(4, idSucursal);
        }
        fecha(statement, 5, desde);
        fecha(statement, 6, desde);
        fecha(statement, 7, hasta);
        fecha(statement, 8, hasta);
    }

    private static void fecha(PreparedStatement statement, int indice, LocalDate fecha)
            throws SQLException {
        if (fecha == null) statement.setNull(indice, Types.DATE);
        else statement.setDate(indice, java.sql.Date.valueOf(fecha));
    }

    private static PedidoResumenDTO mapearResumen(ResultSet rows) throws SQLException {
        return new PedidoResumenDTO(rows.getLong("id_pedido"), rows.getLong("id_sucursal"),
                rows.getString("nombre_sucursal"), rows.getLong("id_usuario"),
                rows.getString("nombre_usuario"), rows.getTimestamp("fecha_pedido").toLocalDateTime(),
                rows.getDate("fecha_entrega").toLocalDate(), rows.getString("estado"),
                rows.getString("observacion"), rows.getLong("cantidad_productos"));
    }

    /** Crea la cabecera y retorna el ID generado; no administra la conexión. */
    public long crear(Connection connection, CrearPedidoDTO pedido) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_CREAR_PEDIDO)) {
            ConnectionFactory.aplicarTimeout(statement);
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
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idPedido);
            statement.setLong(2, detalle.getIdProducto());
            statement.setBigDecimal(3, detalle.getCantidad());
            statement.execute();
        }
    }

    /** Confirma el pedido mediante el paquete Oracle. */
    public void confirmar(Connection connection, long idPedido) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_CONFIRMAR_PEDIDO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idPedido);
            statement.execute();
        }
    }

    /** Cancela el pedido mediante el paquete Oracle; el commit lo hace el servicio. */
    public void cancelar(Connection connection, long idPedido) throws SQLException {
        try (CallableStatement statement = connection.prepareCall(SP_CANCELAR_PEDIDO)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idPedido);
            statement.execute();
        }
    }

    /** Busca la cabecera y sus detalles en la conexión recibida. */
    public Optional<Pedido> buscarPorId(Connection connection, long idPedido)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_BUSCAR_POR_ID)) {
            ConnectionFactory.aplicarTimeout(statement);
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
