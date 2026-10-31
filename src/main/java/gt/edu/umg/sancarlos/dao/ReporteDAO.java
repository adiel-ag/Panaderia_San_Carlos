package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.ConsumoInsumoDTO;
import gt.edu.umg.sancarlos.dto.ReportePedidosSucursalDTO;
import gt.edu.umg.sancarlos.dto.ReporteProduccionDiaDTO;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas de solo lectura para reportes y alertas. Los rangos de fecha son inclusivos. */
public class ReporteDAO {

    // El rango se compara como [desde, hasta + 1) para incluir todo el último día.
    static final String SQL_PEDIDOS_SUCURSAL = """
            SELECT s.id_sucursal, s.nombre AS sucursal, COUNT(*) AS pedidos,
                   SUM(CASE WHEN p.estado = 'DESPACHADO' THEN 1 ELSE 0 END) AS despachados,
                   SUM(CASE WHEN p.estado <> 'DESPACHADO' THEN 1 ELSE 0 END) AS pendientes
              FROM PEDIDO p
              JOIN SUCURSAL s ON s.id_sucursal = p.id_sucursal
             WHERE p.estado <> 'CANCELADO'
               AND p.fecha_entrega >= ? AND p.fecha_entrega < ?
             GROUP BY s.id_sucursal, s.nombre
             ORDER BY s.nombre, s.id_sucursal
            """;

    static final String SQL_PEDIDOS_SUCURSAL_PRODUCTOS = """
            SELECT p.id_sucursal, d.id_producto, pr.nombre AS producto,
                   um.abreviatura AS unidad, SUM(d.cantidad) AS cantidad
              FROM PEDIDO p
              JOIN PEDIDO_DETALLE d ON d.id_pedido = p.id_pedido
              JOIN PRODUCTO pr ON pr.id_producto = d.id_producto
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = pr.id_unidad
             WHERE p.estado <> 'CANCELADO'
               AND p.fecha_entrega >= ? AND p.fecha_entrega < ?
             GROUP BY p.id_sucursal, d.id_producto, pr.nombre, um.abreviatura
             ORDER BY p.id_sucursal, pr.nombre, d.id_producto
            """;

    static final String SQL_PRODUCCION_DIA = """
            SELECT TRUNC(pr.fecha_produccion) AS fecha, COUNT(*) AS producciones
              FROM PRODUCCION pr
             WHERE pr.estado = 'FINALIZADA'
               AND pr.fecha_produccion >= ? AND pr.fecha_produccion < ?
             GROUP BY TRUNC(pr.fecha_produccion)
             ORDER BY fecha
            """;

    static final String SQL_PRODUCCION_DIA_PRODUCTOS = """
            SELECT TRUNC(pr.fecha_produccion) AS fecha, pd.id_producto, p.nombre AS producto,
                   um.abreviatura AS unidad, SUM(pd.cantidad_planificada) AS planificado,
                   SUM(NVL(pd.cantidad_producida, 0)) AS producido
              FROM PRODUCCION pr
              JOIN PRODUCCION_DETALLE pd ON pd.id_produccion = pr.id_produccion
              JOIN PRODUCTO p ON p.id_producto = pd.id_producto
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = p.id_unidad
             WHERE pr.estado = 'FINALIZADA'
               AND pr.fecha_produccion >= ? AND pr.fecha_produccion < ?
             GROUP BY TRUNC(pr.fecha_produccion), pd.id_producto, p.nombre, um.abreviatura
             ORDER BY fecha, p.nombre, pd.id_producto
            """;

    static final String SQL_CONSUMO_INSUMOS = """
            SELECT m.id_producto, p.nombre AS insumo, um.abreviatura AS unidad,
                   SUM(m.cantidad) AS consumido
              FROM MOVIMIENTO_INVENTARIO m
              JOIN PRODUCTO p ON p.id_producto = m.id_producto
              JOIN TIPO_PRODUCTO tp ON tp.id_tipo_producto = p.id_tipo_producto
              JOIN UNIDAD_MEDIDA um ON um.id_unidad = p.id_unidad
             WHERE m.tipo_movimiento = 'SALIDA'
               AND m.origen = 'PRODUCCION'
               AND tp.nombre = 'INSUMO'
               AND m.fecha_movimiento >= ? AND m.fecha_movimiento < ?
             GROUP BY m.id_producto, p.nombre, um.abreviatura
             ORDER BY consumido DESC, p.nombre, m.id_producto
            """;

    static final String SQL_PEDIDOS_ATRASADOS = """
            SELECT COUNT(*)
              FROM PEDIDO p
             WHERE p.fecha_entrega < TRUNC(SYSDATE)
               AND p.estado IN ('PENDIENTE', 'CONFIRMADO', 'EN_PRODUCCION', 'LISTO_DESPACHO')
               AND (? IS NULL OR p.id_sucursal = ?)
            """;

    // Igual que el listado de recetas: sin receta vigente o con más de una vigente.
    static final String SQL_PRODUCTOS_SIN_RECETA = """
            SELECT COUNT(*)
              FROM PRODUCTO p
              JOIN TIPO_PRODUCTO tp ON tp.id_tipo_producto = p.id_tipo_producto
             WHERE p.estado = 'ACTIVO'
               AND tp.nombre = 'PRODUCTO_FINAL'
               AND (SELECT COUNT(*) FROM RECETA r
                     WHERE r.id_producto = p.id_producto AND""" + RecetaDAO.vigente("r") + """
            ) <> 1
            """;

    static final String SQL_INSUMOS_BAJO_MINIMO = """
            SELECT COUNT(*)
              FROM PRODUCTO p
              JOIN TIPO_PRODUCTO tp ON tp.id_tipo_producto = p.id_tipo_producto
              JOIN INVENTARIO i ON i.id_producto = p.id_producto
             WHERE p.estado = 'ACTIVO'
               AND tp.nombre = 'INSUMO'
               AND i.cantidad_actual < p.stock_minimo
            """;

    public List<ReportePedidosSucursalDTO> pedidosPorSucursal(Connection connection,
            LocalDate desde, LocalDate hasta) throws SQLException {
        Map<Long, List<ReportePedidosSucursalDTO.Producto>> productos = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SQL_PEDIDOS_SUCURSAL_PRODUCTOS)) {
            ConnectionFactory.aplicarTimeout(statement);
            rango(statement, desde, hasta);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    productos.computeIfAbsent(rows.getLong("id_sucursal"), k -> new ArrayList<>())
                            .add(new ReportePedidosSucursalDTO.Producto(
                                    rows.getLong("id_producto"), rows.getString("producto"),
                                    rows.getString("unidad"), cero(rows.getBigDecimal("cantidad"))));
                }
            }
        }
        List<ReportePedidosSucursalDTO> resultado = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_PEDIDOS_SUCURSAL)) {
            ConnectionFactory.aplicarTimeout(statement);
            rango(statement, desde, hasta);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long idSucursal = rows.getLong("id_sucursal");
                    resultado.add(new ReportePedidosSucursalDTO(idSucursal,
                            rows.getString("sucursal"), rows.getLong("pedidos"),
                            rows.getLong("despachados"), rows.getLong("pendientes"),
                            productos.getOrDefault(idSucursal, List.of())));
                }
            }
        }
        return resultado;
    }

    public List<ReporteProduccionDiaDTO> produccionPorDia(Connection connection,
            LocalDate desde, LocalDate hasta) throws SQLException {
        Map<LocalDate, List<ReporteProduccionDiaDTO.Producto>> productos = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                SQL_PRODUCCION_DIA_PRODUCTOS)) {
            ConnectionFactory.aplicarTimeout(statement);
            rango(statement, desde, hasta);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    productos.computeIfAbsent(fecha(rows), k -> new ArrayList<>())
                            .add(new ReporteProduccionDiaDTO.Producto(
                                    rows.getLong("id_producto"), rows.getString("producto"),
                                    rows.getString("unidad"),
                                    cero(rows.getBigDecimal("planificado")),
                                    cero(rows.getBigDecimal("producido"))));
                }
            }
        }
        List<ReporteProduccionDiaDTO> resultado = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_PRODUCCION_DIA)) {
            ConnectionFactory.aplicarTimeout(statement);
            rango(statement, desde, hasta);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    LocalDate fecha = fecha(rows);
                    resultado.add(new ReporteProduccionDiaDTO(fecha,
                            rows.getLong("producciones"), productos.getOrDefault(fecha, List.of())));
                }
            }
        }
        return resultado;
    }

    public List<ConsumoInsumoDTO> consumoInsumos(Connection connection, LocalDate desde,
            LocalDate hasta) throws SQLException {
        List<ConsumoInsumoDTO> resultado = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SQL_CONSUMO_INSUMOS)) {
            ConnectionFactory.aplicarTimeout(statement);
            rango(statement, desde, hasta);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    resultado.add(new ConsumoInsumoDTO(rows.getLong("id_producto"),
                            rows.getString("insumo"), rows.getString("unidad"),
                            cero(rows.getBigDecimal("consumido"))));
                }
            }
        }
        return resultado;
    }

    /** Pedidos con entrega vencida sin despachar; idSucursal nulo cuenta todas. */
    public long contarPedidosAtrasados(Connection connection, Long idSucursal)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_PEDIDOS_ATRASADOS)) {
            ConnectionFactory.aplicarTimeout(statement);
            if (idSucursal == null) {
                statement.setNull(1, Types.NUMERIC);
                statement.setNull(2, Types.NUMERIC);
            } else {
                statement.setLong(1, idSucursal);
                statement.setLong(2, idSucursal);
            }
            return contar(statement);
        }
    }

    public long contarProductosSinReceta(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_PRODUCTOS_SIN_RECETA)) {
            ConnectionFactory.aplicarTimeout(statement);
            return contar(statement);
        }
    }

    public long contarInsumosBajoMinimo(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_INSUMOS_BAJO_MINIMO)) {
            ConnectionFactory.aplicarTimeout(statement);
            return contar(statement);
        }
    }

    private static void rango(PreparedStatement statement, LocalDate desde, LocalDate hasta)
            throws SQLException {
        statement.setDate(1, Date.valueOf(desde));
        statement.setDate(2, Date.valueOf(hasta.plusDays(1)));
    }

    private static long contar(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : 0;
        }
    }

    private static LocalDate fecha(ResultSet rows) throws SQLException {
        Date fecha = rows.getDate("fecha");
        return fecha == null ? null : fecha.toLocalDate();
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
