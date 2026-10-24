package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.CrearPedidoDetalleDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PedidoServiceIT {

    private final PedidoService pedidoService = new PedidoService();

    @Test
    void createsAndConfirmsPedidoWithTwoProducts() throws SQLException {
        TestIds ids = configuredTestIds();
        Assumptions.assumeTrue(ids != null,
                "Configura las variables PEDIDO_TEST_* con IDs válidos de Oracle");
        Assumptions.assumeTrue(ids.producto2() != null,
                "Configura PEDIDO_TEST_ID_PRODUCTO_2 con otro producto válido");
        assertNotEquals(ids.producto1(), ids.producto2(),
                "La prueba requiere dos productos diferentes");

        long idPedido = pedidoService.crearPedido(
                crearSolicitud(ids, List.of(ids.producto1(), ids.producto2()),
                        "Prueba de pedido confirmado")
        );

        assertTrue(idPedido > 0, "Oracle debe devolver el ID del pedido");
        System.out.println("Pedido confirmado por Oracle. ID retornado: " + idPedido);
    }

    @Test
    void rollsBackHeaderAndEarlierDetailWhenLaterProductFails() throws SQLException {
        TestIds ids = configuredTestIds();
        Assumptions.assumeTrue(ids != null,
                "Configura usuario, sucursal y un producto válido para probar rollback");
        String observacion = "TEST_ROLLBACK_" + UUID.randomUUID();

        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> pedidoService.crearPedido(
                        crearSolicitud(ids,
                                List.of(ids.producto1(), Long.MAX_VALUE),
                                observacion)
                )
        );

        SQLException oracleException = findSqlException(exception);
        assertNotNull(oracleException, "Debe conservarse la excepción original de Oracle");
        assertEquals(20310, Math.abs(oracleException.getErrorCode()));

        try (Connection connection = ConnectionFactory.getConnection()) {
            assertEquals(0, countPedidosByObservacion(connection, observacion));
            assertEquals(0, countDetallesByObservacion(connection, observacion));
        }
        System.out.println("Rollback confirmado: no quedó pedido ni detalle parcial.");
    }

    private static CrearPedidoDTO crearSolicitud(
            TestIds ids,
            List<Long> productos,
            String observacion
    ) {
        CrearPedidoDTO pedido = new CrearPedidoDTO();
        pedido.setIdSucursal(ids.sucursal());
        pedido.setIdUsuario(ids.usuario());
        pedido.setFechaEntrega(LocalDate.now().plusDays(2));
        pedido.setObservacion(observacion);
        pedido.setDetalles(productos.stream()
                .map(idProducto -> new CrearPedidoDetalleDTO(idProducto, 1))
                .toList());
        return pedido;
    }

    private static TestIds configuredTestIds() {
        String sucursal = System.getenv("PEDIDO_TEST_ID_SUCURSAL");
        String usuario = System.getenv("PEDIDO_TEST_ID_USUARIO");
        String producto1 = System.getenv("PEDIDO_TEST_ID_PRODUCTO_1");
        String producto2 = System.getenv("PEDIDO_TEST_ID_PRODUCTO_2");
        if (isBlank(sucursal) || isBlank(usuario) || isBlank(producto1)) {
            return null;
        }
        return new TestIds(
                Long.parseLong(sucursal),
                Long.parseLong(usuario),
                Long.parseLong(producto1),
                isBlank(producto2) ? null : Long.parseLong(producto2)
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static SQLException findSqlException(Throwable exception) {
        Throwable current = exception;
        while (current != null && !(current instanceof SQLException)) {
            current = current.getCause();
        }
        return (SQLException) current;
    }

    private static int countPedidosByObservacion(
            Connection connection,
            String observacion
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM PEDIDO WHERE observacion = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, observacion);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static int countDetallesByObservacion(
            Connection connection,
            String observacion
    ) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM PEDIDO_DETALLE d
                  JOIN PEDIDO p ON p.id_pedido = d.id_pedido
                 WHERE p.observacion = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, observacion);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private record TestIds(long sucursal, long usuario, long producto1, Long producto2) {
    }
}
