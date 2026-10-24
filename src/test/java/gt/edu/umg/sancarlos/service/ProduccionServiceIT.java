package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.exception.DataAccessException;
import java.sql.SQLException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProduccionServiceIT {

    private final ProduccionService produccionService = new ProduccionService();

    @Test
    void createsProductionAndPrintsGeneratedId() {
        String idUsuarioConfigurado = System.getenv("PRODUCCION_TEST_ID_USUARIO");
        Assumptions.assumeTrue(
                idUsuarioConfigurado != null && !idUsuarioConfigurado.isBlank(),
                "Configura PRODUCCION_TEST_ID_USUARIO con el ID de un usuario activo"
        );

        long idUsuario = Long.parseLong(idUsuarioConfigurado);
        long idProduccion = produccionService.crearProduccion(
                idUsuario,
                "Prueba de integración JDBC"
        );

        assertTrue(idProduccion > 0, "Oracle debe devolver un ID generado");
        System.out.println("Producción creada. ID retornado por Oracle: " + idProduccion);
    }

    @Test
    void rejectsNonexistentUserWithOracleApplicationError() {
        DataAccessException exception = assertThrows(
                DataAccessException.class,
                () -> produccionService.crearProduccion(
                        Long.MAX_VALUE,
                        "Prueba de usuario inexistente"
                )
        );

        SQLException oracleException = findSqlException(exception);
        assertNotNull(oracleException, "La excepción debe conservar el error JDBC de Oracle");
        assertEquals(20401, Math.abs(oracleException.getErrorCode()));
        assertTrue(oracleException.getMessage().contains("Usuario inexistente o inactivo"));
        System.out.println("Error Oracle esperado: " + oracleException.getMessage());
    }

    private static SQLException findSqlException(Throwable exception) {
        Throwable current = exception;
        while (current != null && !(current instanceof SQLException)) {
            current = current.getCause();
        }
        return (SQLException) current;
    }
}
