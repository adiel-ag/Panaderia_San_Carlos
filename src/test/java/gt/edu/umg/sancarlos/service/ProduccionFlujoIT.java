package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.model.ProduccionDetalle;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProduccionFlujoIT {

    @Test
    void completaFlujoDeProduccionConOracle() {
        String usuario = System.getenv("PRODUCCION_TEST_ID_USUARIO");
        String pedidos = System.getenv("PRODUCCION_TEST_ID_PEDIDOS");
        Assumptions.assumeTrue(usuario != null && !usuario.isBlank()
                && pedidos != null && !pedidos.isBlank(),
                "Configura un usuario activo y uno o más pedidos confirmados de prueba");

        long idUsuario = Long.parseLong(usuario);
        List<Long> idPedidos = Arrays.stream(pedidos.split(","))
                .map(String::trim).map(Long::parseLong).toList();
        ProduccionService service = new ProduccionService();

        long idProduccion = service.crearYGenerarPlan(
                idUsuario, "Prueba integrada " + System.currentTimeMillis(), idPedidos);
        List<ProduccionDetalle> detalle = service.consultarDetalle(idProduccion);
        assertFalse(detalle.isEmpty(), "Oracle debe generar al menos una línea de producción");

        for (ProduccionDetalle item : detalle) {
            BigDecimal cantidad = item.getCantidadPlanificada();
            assertTrue(cantidad.signum() > 0, "La cantidad planificada debe ser positiva");
            service.registrarProducido(idProduccion, item.getIdProducto(), cantidad);
        }
        service.finalizarProduccion(idProduccion, idUsuario);
        System.out.println("Producción integrada finalizada. ID: " + idProduccion);
    }
}
