package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.exception.AccesoDenegadoException;
import gt.edu.umg.sancarlos.exception.CredencialesInvalidasException;
import gt.edu.umg.sancarlos.exception.CuentaBloqueadaException;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.NoAutenticadoException;
import gt.edu.umg.sancarlos.exception.UsuarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.DespachoConflictoException;
import gt.edu.umg.sancarlos.exception.DespachoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.InventarioConflictoException;
import gt.edu.umg.sancarlos.exception.InventarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.InventarioSolicitudInvalidaException;
import gt.edu.umg.sancarlos.exception.PedidoConflictoException;
import gt.edu.umg.sancarlos.exception.PedidoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.PedidoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.exception.ProduccionConflictoException;
import gt.edu.umg.sancarlos.exception.ProduccionNoEncontradaException;
import gt.edu.umg.sancarlos.exception.ProduccionSolicitudInvalidaException;
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
    private static final String JSON_INVALIDO = "El cuerpo JSON no es válido.";

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof JsonProcessingException) {
            // El mensaje de Jackson incluye detalles del parser; se responde con uno fijo.
            return crearRespuesta(Response.Status.BAD_REQUEST,
                    "Solicitud inválida", JSON_INVALIDO);
        }
        if (exception instanceof CredencialesInvalidasException
                || exception instanceof CuentaBloqueadaException
                || exception instanceof NoAutenticadoException) {
            return crearRespuesta(Response.Status.UNAUTHORIZED,
                    "No autenticado", exception.getMessage());
        }
        if (exception instanceof AccesoDenegadoException) {
            return crearRespuesta(Response.Status.FORBIDDEN,
                    "Acceso denegado", exception.getMessage());
        }
        if (exception instanceof BadRequestException
                || exception instanceof IllegalArgumentException
                || exception instanceof PedidoSolicitudInvalidaException
                || exception instanceof ProduccionSolicitudInvalidaException
                || exception instanceof InventarioSolicitudInvalidaException) {
            return crearRespuesta(Response.Status.BAD_REQUEST,
                    "Solicitud inválida", exception.getMessage());
        }
        if (exception instanceof NotFoundException
                || exception instanceof RecetaNoEncontradaException
                || exception instanceof PedidoNoEncontradoException
                || exception instanceof ProduccionNoEncontradaException
                || exception instanceof InventarioNoEncontradoException
                || exception instanceof DespachoNoEncontradoException
                || exception instanceof UsuarioNoEncontradoException) {
            return crearRespuesta(Response.Status.NOT_FOUND,
                    "Recurso no encontrado", exception.getMessage());
        }
        if (exception instanceof RecetaAmbiguaException
                || exception instanceof PedidoConflictoException
                || exception instanceof ProduccionConflictoException
                || exception instanceof InventarioConflictoException
                || exception instanceof DespachoConflictoException) {
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
