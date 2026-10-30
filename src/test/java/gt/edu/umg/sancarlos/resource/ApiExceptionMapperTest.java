package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.dto.MovimientoInventarioDTO;
import java.util.List;
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
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void convierteErroresDeProduccionEn400404Y409() {
        try (Response badRequest = mapper.toResponse(new ProduccionSolicitudInvalidaException(
                        "La cantidad producida debe ser mayor que cero", new SQLException("ORA-20409")));
             Response notFound = mapper.toResponse(new ProduccionNoEncontradaException(
                        "Usuario inexistente o inactivo", new SQLException("ORA-20401")));
             Response conflict = mapper.toResponse(new ProduccionConflictoException(
                        "El plan de producción ya fue generado", new SQLException("ORA-20407")))) {
            assertEquals(400, badRequest.getStatus());
            assertEquals(404, notFound.getStatus());
            assertEquals(409, conflict.getStatus());
            assertEquals("El plan de producción ya fue generado",
                    ((ApiError) conflict.getEntity()).getMessage());
        }
    }

    @Test
    void jsonMalFormadoOConTipoIncorrectoResponde400SinDetalleDelParser() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonProcessingException truncado = assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue("{\"cantidad\":", MovimientoInventarioDTO.class));
        JsonProcessingException tipoIncorrecto = assertThrows(JsonProcessingException.class,
                () -> objectMapper.readValue("{\"cantidad\":\"abc\"}", MovimientoInventarioDTO.class));

        for (JsonProcessingException exception : List.of(truncado, tipoIncorrecto)) {
            try (Response response = mapper.toResponse(exception)) {
                assertEquals(400, response.getStatus());
                assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
                String message = ((ApiError) response.getEntity()).getMessage();
                assertEquals("El cuerpo JSON no es válido.", message);
                assertFalse(message.contains("line"));
            }
        }
    }

    @Test
    void convierteErroresDeSesionEn401403Y404() {
        try (Response credenciales = mapper.toResponse(new CredencialesInvalidasException(
                        "Usuario o contraseña incorrectos.", new SQLException("ORA-20602")));
             Response bloqueada = mapper.toResponse(new CuentaBloqueadaException(
                        "La cuenta está bloqueada temporalmente", new SQLException("ORA-20603")));
             Response sinSesion = mapper.toResponse(new NoAutenticadoException("Debe iniciar sesión.", null));
             Response denegado = mapper.toResponse(new AccesoDenegadoException("Sin permiso", null));
             Response usuario = mapper.toResponse(new UsuarioNoEncontradoException("No existe", null))) {
            assertEquals(401, credenciales.getStatus());
            assertEquals(401, bloqueada.getStatus());
            assertEquals(401, sinSesion.getStatus());
            assertEquals(403, denegado.getStatus());
            assertEquals(404, usuario.getStatus());
            assertEquals("Usuario o contraseña incorrectos.",
                    ((ApiError) credenciales.getEntity()).getMessage());
            assertFalse(((ApiError) credenciales.getEntity()).getMessage().contains("ORA-"));
        }
    }

    @Test
    void convierteErroresDeDespachoEn404Y409() {
        try (Response notFound = mapper.toResponse(new DespachoNoEncontradoException(
                        "Pedido inexistente", new SQLException("ORA-01403")));
             Response conflict = mapper.toResponse(new DespachoConflictoException(
                        "El pedido aún no está listo para despacho", new SQLException("ORA-20501")))) {
            assertEquals(404, notFound.getStatus());
            assertEquals(409, conflict.getStatus());
        }
    }

    @Test
    void convierteErroresDeInventarioEn400404Y409() {
        try (Response badRequest = mapper.toResponse(new InventarioSolicitudInvalidaException(
                        "La cantidad debe ser mayor que cero", new SQLException("ORA-20001")));
             Response notFound = mapper.toResponse(new InventarioNoEncontradoException(
                        "Usuario inexistente", new SQLException("ORA-02291")));
             Response conflict = mapper.toResponse(new InventarioConflictoException(
                        "Inventario insuficiente", new SQLException("ORA-20003")))) {
            assertEquals(400, badRequest.getStatus());
            assertEquals(404, notFound.getStatus());
            assertEquals(409, conflict.getStatus());
        }
    }
}


