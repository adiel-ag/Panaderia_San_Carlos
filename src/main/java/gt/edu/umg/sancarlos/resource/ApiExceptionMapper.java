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
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Convierte excepciones de recursos en respuestas JSON consistentes. */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOGGER = Logger.getLogger(ApiExceptionMapper.class.getName());
    private static final String ERROR_INTERNO = "Ocurrió un error interno en el servidor.";

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof BadRequestException
                || exception instanceof IllegalArgumentException
                || exception instanceof PedidoSolicitudInvalidaException) {
            return crearRespuesta(Response.Status.BAD_REQUEST,
                    "Solicitud inválida", exception.getMessage());
        }
        if (exception instanceof NotFoundException
                || exception instanceof RecetaNoEncontradaException
                || exception instanceof PedidoNoEncontradoException) {
            return crearRespuesta(Response.Status.NOT_FOUND,
                    "Recurso no encontrado", exception.getMessage());
        }
        if (exception instanceof RecetaAmbiguaException
                || exception instanceof PedidoConflictoException) {
            return crearRespuesta(Response.Status.CONFLICT,
                    "Conflicto de negocio", exception.getMessage());
        }
        if (exception instanceof DataAccessException) {
            LOGGER.log(Level.SEVERE, "Error de acceso a datos en REST", exception);
            return crearRespuesta(Response.Status.INTERNAL_SERVER_ERROR,
                    "Error interno", ERROR_INTERNO);
        }
        if (exception instanceof WebApplicationException webException) {
            Response response = webException.getResponse();
            Response.StatusType statusInfo = response.getStatusInfo();
            String reason = statusInfo == null ? "Error HTTP" : statusInfo.getReasonPhrase();
            return Response.status(response.getStatus())
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new ApiError(response.getStatus(), reason, reason))
                    .build();
        }
        LOGGER.log(Level.SEVERE, "Error inesperado procesando una solicitud REST", exception);
        return crearRespuesta(Response.Status.INTERNAL_SERVER_ERROR,
                "Error interno", ERROR_INTERNO);
    }

    private Response crearRespuesta(Response.Status status, String error, String message) {
        String safeMessage = message == null || message.isBlank() ? error : message;
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ApiError(status.getStatusCode(), error, safeMessage))
                .build();
    }
}
