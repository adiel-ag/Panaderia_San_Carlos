package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.ExistenciaResumenDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.MovimientoDTO;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Acceso JDBC a las operaciones expuestas por PKG_INVENTARIO. */
public class InventarioDAO {

    private static final String SQL_MOVIMIENTOS_BASE = """
              FROM MOVIMIENTO_INVENTARIO m
              LEFT JOIN USUARIO u ON u.id_usuario = m.id_usuario
              -- En los movimientos de despacho, id_referencia es el ID del despacho.
              LEFT JOIN DESPACHO d ON m.origen = 'DESPACHO' AND d.id_despacho = m.id_referencia
             WHERE m.id_producto = ?
            """;

    public Pagina<MovimientoDTO> listarMovimientos(Connection connection, long idProducto,
            int pagina, int tamano) throws SQLException {
        long total;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*)" + SQL_MOVIMIENTOS_BASE)) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                total = rows.getLong(1);
            }
        }
        List<MovimientoDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT m.id_movimiento, m.fecha_movimiento, m.tipo_movimiento,
                       m.cantidad, m.origen, m.id_referencia, u.nombre AS usuario,
                       m.observacion, d.id_pedido
                """ + SQL_MOVIMIENTOS_BASE
                + " ORDER BY m.fecha_movimiento DESC, m.id_movimiento DESC"
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            ConnectionFactory.aplicarTimeout(statement);
            statement.setLong(1, idProducto);
            statement.setLong(2, ((long) pagina - 1) * tamano);
            statement.setInt(3, tamano);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long referencia = rows.getLong("id_referencia");
                    Long idReferencia = rows.wasNull() ? null : referencia;
                    long pedido = rows.getLong("id_pedido");
                    Long idPedido = rows.wasNull() ? null : pedido;
                    items.add(new MovimientoDTO(rows.getLong("id_movimiento"),
                            rows.getTimestamp("fecha_movimiento").toLocalDateTime(),
                            rows.getString("tipo_movimiento"), rows.getBigDecimal("cantidad"),
                            rows.getString("origen"), idReferencia,
                            rows.getString("usuario"), rows.getString("observacion"), idPedido));
                }
            }
        }
        return new Pagina<>(items, total, pagina, tamano);
    }

    private static final String SQL_BASE = """
              FROM PRODUCTO p
              JOIN TIPO_PRODUCTO tp ON tp.id_tipo_producto = p.id_tipo_producto
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = p.id_unidad
              LEFT JOIN INVENTARIO i ON i.id_producto = p.id_producto
            """;
    private static final String SQL_FILTROS = """
             WHERE p.estado = 'ACTIVO'
               AND (? IS NULL OR tp.nombre = ?)
               AND (? IS NULL OR UPPER(p.nombre) LIKE '%' || UPPER(?) || '%' ESCAPE '\\')
               AND (? IS NULL OR (i.id_producto IS NOT NULL AND
                    CASE WHEN i.cantidad_actual < p.stock_minimo THEN 1 ELSE 0 END = ?))
            """;
    private static final String SQL_COLUMNAS = """
            SELECT p.id_producto, p.nombre, tp.nombre AS tipo, um.abreviatura AS unidad,
                   i.cantidad_actual AS existencia, p.stock_minimo,
                   CASE WHEN i.id_producto IS NULL THEN NULL
                        WHEN i.cantidad_actual < p.stock_minimo THEN 1 ELSE 0
                   END AS bajo_minimo
            """;

    /** Pagina productos activos y distingue inventario ausente de existencia cero. */
    public Pagina<ExistenciaResumenDTO> listarExistencias(Connection connection, int pagina,
            int tamano, String tipo, String nombre, Boolean bajoMinimo) throws SQLException {
        long total;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*)" + SQL_BASE + SQL_FILTROS)) {
            ConnectionFactory.aplicarTimeout(statement);
            filtros(statement, tipo, nombre, bajoMinimo);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                total = rows.getLong(1);
            }
        }
        List<ExistenciaResumenDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_COLUMNAS + SQL_BASE
                + SQL_FILTROS + " ORDER BY p.nombre, p.id_producto"
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            ConnectionFactory.aplicarTimeout(statement);
            filtros(statement, tipo, nombre, bajoMinimo);
            statement.setLong(7, ((long) pagina - 1) * tamano);
            statement.setInt(8, tamano);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int minimo = rows.getInt("bajo_minimo");
                    Boolean bajo = rows.wasNull() ? null : minimo == 1;
                    items.add(new ExistenciaResumenDTO(rows.getLong("id_producto"),
                            rows.getString("nombre"), rows.getString("tipo"),
                            rows.getString("unidad"), rows.getBigDecimal("existencia"),
                            rows.getBigDecimal("stock_minimo"), bajo));
                }
            }
        }
        return new Pagina<>(items, total, pagina, tamano);
    }

    private static void filtros(PreparedStatement statement, String tipo, String nombre,
            Boolean bajoMinimo) throws SQLException {
        statement.setString(1, tipo);
        statement.setString(2, tipo);
        statement.setString(3, nombre);
        statement.setString(4, nombre);
        if (bajoMinimo == null) {
            statement.setNull(5, Types.NUMERIC);
            statement.setNull(6, Types.NUMERIC);
        } else {
            statement.setInt(5, bajoMinimo ? 1 : 0);
            statement.setInt(6, bajoMinimo ? 1 : 0);
        }
    }

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
