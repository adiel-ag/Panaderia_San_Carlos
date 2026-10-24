package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.PedidoConflictoException;
import gt.edu.umg.sancarlos.exception.PedidoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.PedidoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.exception.RecetaAmbiguaException;
import gt.edu.umg.sancarlos.exception.RecetaNoEncontradaException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiExceptionMapperTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void serializaErroresRestComoJsonConCodigoCorrecto() {
        try (Response badRequest = mapper.toResponse(new BadRequestException("id inválido"));
             Response notFound = mapper.toResponse(new NotFoundException("producto inexistente"));
             Response conflict = mapper.toResponse(new WebApplicationException(
                     Response.status(Response.Status.CONFLICT).build()))) {
            assertEquals(400, badRequest.getStatus());
            assertEquals(404, notFound.getStatus());
            assertEquals(409, conflict.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, badRequest.getMediaType());
            assertEquals("id inválido", ((ApiError) badRequest.getEntity()).getMessage());
        }
    }

    @Test
    void convierteErroresOracleDeRecetaEn404Y409() {
        try (Response notFound = mapper.toResponse(new RecetaNoEncontradaException(
                        "El producto no posee una receta activa", new SQLException("ORA-20201")));
             Response conflict = mapper.toResponse(new RecetaAmbiguaException(
                        "El producto posee más de una receta activa", new SQLException("ORA-20202")))) {
            assertEquals(404, notFound.getStatus());
            assertEquals(409, conflict.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, notFound.getMediaType());
        }
    }

    @Test
    void ocultaDetallesInternosEnError500() {
        DataAccessException exception = new DataAccessException(
                "SQL interno", new SQLException("detalle Oracle"));
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(500, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            String message = ((ApiError) response.getEntity()).getMessage();
            assertFalse(message.contains("SQL interno"));
            assertFalse(message.contains("detalle Oracle"));
        }
    }
    @Test
    void validaSolicitudIncorrectaDePedidoComo400() {
        try (Response response = mapper.toResponse(
                new IllegalArgumentException("La fecha de entrega es obligatoria"))) {
            assertEquals(400, response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        }
    }
    @Test
    void convierteErroresDePedidoEn400404Y409() {
        try (Response badRequest = mapper.toResponse(new PedidoSolicitudInvalidaException(
                        "cantidad inválida", new SQLException("ORA-20305")));
             Response notFound = mapper.toResponse(new PedidoNoEncontradoException(
                        "Pedido inexistente", new SQLException("ORA-20310")));
             Response conflict = mapper.toResponse(new PedidoConflictoException(
                        "El producto ya está en el pedido", new SQLException("ORA-20309")))) {
            assertEquals(400, badRequest.getStatus());
            assertEquals(404, notFound.getStatus());
            assertEquals(409, conflict.getStatus());
        }
    }
}


