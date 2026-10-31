package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.dto.ExistenciaDTO;
import gt.edu.umg.sancarlos.dto.MovimientoInventarioDTO;
import gt.edu.umg.sancarlos.dto.MovimientoDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.exception.InventarioConflictoException;
import gt.edu.umg.sancarlos.exception.InventarioNoEncontradoException;
import gt.edu.umg.sancarlos.service.InventarioService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventarioResourceTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();

    @Test
    void historialValidaPaginaYResponde200O404() throws Exception {
        Method metodo = InventarioResource.class.getMethod("listarMovimientos",
                String.class, String.class, String.class);
        assertNotNull(metodo.getAnnotation(GET.class));
        assertEquals("productos/{idProducto}/movimientos", metodo.getAnnotation(Path.class).value());
        assertEquals(Set.of("ADMINISTRADOR", "GERENTE", "PRODUCCION", "BODEGA", "DESPACHO"),
                Set.of(metodo.getAnnotation(RolesAllowed.class).value()));
        InventarioResource recurso = new InventarioResource(new InventarioService() {
            @Override public Pagina<MovimientoDTO> listarMovimientos(long id, int pagina, int tamano) {
                if (id == 99) throw new InventarioNoEncontradoException("No existe", null);
                return new Pagina<>(List.of(), 0, pagina, tamano);
            }
        });
        try (Response respuesta = recurso.listarMovimientos("5", null, null)) {
            assertEquals(200, respuesta.getStatus());
            Pagina<?> pagina = assertInstanceOf(Pagina.class, respuesta.getEntity());
            assertEquals(1, pagina.pagina());
            assertEquals(20, pagina.tamano());
        }
        assertThrows(BadRequestException.class, () -> recurso.listarMovimientos("0", null, null));
        assertThrows(IllegalArgumentException.class, () -> recurso.listarMovimientos("5", "1", "101"));
        try (Response respuesta = mapper.toResponse(assertThrows(
                InventarioNoEncontradoException.class,
                () -> recurso.listarMovimientos("99", "1", "20")))) {
            assertEquals(404, respuesta.getStatus());
        }
    }

    @Test
    void exponeRutasYMetodosHttp() throws Exception {
        assertEquals("/inventario", InventarioResource.class.getAnnotation(Path.class).value());

        Method existencia = InventarioResource.class.getMethod("consultarExistencia", String.class);
        assertNotNull(existencia.getAnnotation(GET.class));
        assertEquals("productos/{idProducto}/existencia", existencia.getAnnotation(Path.class).value());

        Method entrada = InventarioResource.class.getMethod("registrarEntrada",
                String.class, MovimientoInventarioDTO.class, SecurityContext.class);
        assertNotNull(entrada.getAnnotation(POST.class));
        assertEquals("productos/{idProducto}/entradas", entrada.getAnnotation(Path.class).value());
        assertEquals(MediaType.APPLICATION_JSON, entrada.getAnnotation(Consumes.class).value()[0]);

        Method salida = InventarioResource.class.getMethod("registrarSalida",
                String.class, MovimientoInventarioDTO.class, SecurityContext.class);
        assertNotNull(salida.getAnnotation(POST.class));
        assertEquals("productos/{idProducto}/salidas", salida.getAnnotation(Path.class).value());
    }

    @Test
    void consultaExistenciaCon200() {
        InventarioService service = new InventarioService() {
            @Override
            public ExistenciaDTO consultarExistencia(long idProducto) {
                return new ExistenciaDTO(idProducto, new BigDecimal("15.500"));
            }
        };

        try (Response response = new InventarioResource(service).consultarExistencia("35")) {
            assertEquals(200, response.getStatus());
            ExistenciaDTO body = assertInstanceOf(ExistenciaDTO.class, response.getEntity());
            assertEquals(35, body.getIdProducto());
            assertEquals(new BigDecimal("15.500"), body.getExistencia());
        }
    }

    @Test
    void productoInexistenteResponde404() {
        InventarioService service = new InventarioService() {
            @Override
            public ExistenciaDTO consultarExistencia(long idProducto) {
                throw new InventarioNoEncontradoException("No existe un producto con ID 99", null);
            }
        };

        InventarioNoEncontradoException exception = assertThrows(InventarioNoEncontradoException.class,
                () -> new InventarioResource(service).consultarExistencia("99"));
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(404, response.getStatus());
        }
    }

    @Test
    void registraEntradaYSalidaCon200YExistenciaResultante() {
        List<Object> llamadas = new ArrayList<>();
        InventarioService service = new InventarioService() {
            @Override
            public ExistenciaDTO registrarEntrada(long idProducto, long idUsuario,
                    BigDecimal cantidad, String observacion) {
                llamadas.add("entrada");
                llamadas.add(idProducto);
                llamadas.add(idUsuario);
                llamadas.add(cantidad);
                llamadas.add(observacion);
                return new ExistenciaDTO(idProducto, new BigDecimal("20"));
            }

            @Override
            public ExistenciaDTO registrarSalida(long idProducto, long idUsuario,
                    BigDecimal cantidad, String observacion) {
                llamadas.add("salida");
                return new ExistenciaDTO(idProducto, new BigDecimal("15"));
            }
        };
        InventarioResource resource = new InventarioResource(service);

        try (Response entrada = resource.registrarEntrada("35", movimiento("5", "Conteo físico"), SesionDePrueba.usuario(7, "BODEGA"));
             Response salida = resource.registrarSalida("35", movimiento("5", null), SesionDePrueba.usuario(7, "BODEGA"))) {
            assertEquals(200, entrada.getStatus());
            assertEquals(new BigDecimal("20"),
                    assertInstanceOf(ExistenciaDTO.class, entrada.getEntity()).getExistencia());
            assertEquals(200, salida.getStatus());
        }
        assertEquals(List.of("entrada", 35L, 7L, new BigDecimal("5"), "Conteo físico", "salida"),
                llamadas);
    }

    @Test
    void rechazaIdsCuerpoYCantidadesInvalidasCon400() {
        InventarioResource resource = new InventarioResource(new InventarioService());

        assertThrows(BadRequestException.class, () -> resource.consultarExistencia("abc"));
        assertThrows(BadRequestException.class, () -> resource.consultarExistencia("0"));
        assertThrows(BadRequestException.class, () -> resource.registrarEntrada("-1", movimiento("1", null), SesionDePrueba.usuario(7, "BODEGA")));
        assertThrows(BadRequestException.class, () -> resource.registrarEntrada("35", null, SesionDePrueba.usuario(7, "BODEGA")));
        assertThrows(BadRequestException.class, () -> resource.registrarSalida("35", null, SesionDePrueba.usuario(7, "BODEGA")));
        assertThrows(BadRequestException.class,
                () -> resource.registrarEntrada("35", new MovimientoInventarioDTO(), SesionDePrueba.usuario(7, "BODEGA")));
        assertThrows(BadRequestException.class, () -> resource.registrarEntrada("35", movimiento("0", null), SesionDePrueba.usuario(7, "BODEGA")));
        assertThrows(BadRequestException.class, () -> resource.registrarSalida("35", movimiento("-2", null), SesionDePrueba.usuario(7, "BODEGA")));
    }

    @Test
    void inventarioInsuficienteResponde409SinTextoSql() {
        InventarioService service = new InventarioService() {
            @Override
            public ExistenciaDTO registrarSalida(long idProducto, long idUsuario,
                    BigDecimal cantidad, String observacion) {
                throw new InventarioConflictoException("Inventario insuficiente",
                        new SQLException("ORA-20003: Inventario insuficiente ORA-06512", "72000", 20003));
            }
        };

        InventarioConflictoException exception = assertThrows(InventarioConflictoException.class,
                () -> new InventarioResource(service).registrarSalida("35", movimiento("500", null),
                        SesionDePrueba.usuario(7, "BODEGA")));
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(409, response.getStatus());
            assertFalse(((ApiError) response.getEntity()).getMessage().contains("ORA-"));
        }
    }

    private static MovimientoInventarioDTO movimiento(String cantidad, String observacion) {
        MovimientoInventarioDTO dto = new MovimientoInventarioDTO();
        dto.setCantidad(new BigDecimal(cantidad));
        dto.setObservacion(observacion);
        return dto;
    }
}
