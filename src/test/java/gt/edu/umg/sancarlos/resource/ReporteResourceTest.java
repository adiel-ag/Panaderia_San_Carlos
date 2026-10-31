package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.AlertasDTO;
import gt.edu.umg.sancarlos.dto.ApiError;
import gt.edu.umg.sancarlos.dto.ConsumoInsumoDTO;
import gt.edu.umg.sancarlos.dto.ReportePedidosSucursalDTO;
import gt.edu.umg.sancarlos.dto.ReporteProduccionDiaDTO;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.AlertaService;
import gt.edu.umg.sancarlos.service.ReporteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReporteResourceTest {

    private final ApiExceptionMapper mapper = new ApiExceptionMapper();
    private final List<LocalDate> fechas = new ArrayList<>();

    private ReporteResource recurso() {
        return new ReporteResource(new ReporteService() {
            @Override public List<ReportePedidosSucursalDTO> pedidosPorSucursal(LocalDate d,
                    LocalDate h) {
                fechas.add(d);
                fechas.add(h);
                return List.of(new ReportePedidosSucursalDTO(1, "Centro", 2, 1, 1, List.of()));
            }
            @Override public List<ReporteProduccionDiaDTO> produccionPorDia(LocalDate d,
                    LocalDate h) {
                return List.of();
            }
            @Override public List<ConsumoInsumoDTO> consumoInsumos(LocalDate d, LocalDate h) {
                return List.of(new ConsumoInsumoDTO(2, "Harina", "KG", BigDecimal.TEN));
            }
        });
    }

    @Test
    void exponeRutasGetSoloParaAdministradorYGerente() throws Exception {
        assertEquals("/reportes", ReporteResource.class.getAnnotation(Path.class).value());
        for (String[] caso : new String[][]{{"pedidosPorSucursal", "pedidos-por-sucursal"},
                {"produccionPorDia", "produccion-por-dia"}, {"consumoInsumos", "consumo-insumos"}}) {
            Method metodo = ReporteResource.class.getMethod(caso[0], String.class, String.class);
            assertNotNull(metodo.getAnnotation(GET.class));
            assertEquals(caso[1], metodo.getAnnotation(Path.class).value());
            assertEquals(Set.of("ADMINISTRADOR", "GERENTE"),
                    Set.of(metodo.getAnnotation(RolesAllowed.class).value()));
        }
    }

    @Test
    void rangoValidoResponde200ConFechasInclusivas() {
        try (Response respuesta = recurso().pedidosPorSucursal("2026-10-01", "2026-10-31")) {
            assertEquals(200, respuesta.getStatus());
            assertEquals(1, assertInstanceOf(List.class, respuesta.getEntity()).size());
        }
        assertEquals(List.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), fechas);
        try (Response respuesta = recurso().consumoInsumos("2026-10-01", "2026-10-01")) {
            assertEquals(200, respuesta.getStatus());
        }
        try (Response respuesta = recurso().produccionPorDia("2026-01-01", "2026-12-31")) {
            assertEquals(200, respuesta.getStatus());
        }
    }

    @Test
    void fechasFaltantesInvalidasOInvertidasResponden400() {
        ReporteResource recurso = recurso();
        List<Runnable> casos = List.of(
                () -> recurso.pedidosPorSucursal(null, "2026-10-31"),
                () -> recurso.pedidosPorSucursal("2026-10-01", ""),
                () -> recurso.produccionPorDia("01/10/2026", "2026-10-31"),
                () -> recurso.consumoInsumos("2026-10-31", "2026-10-01"),
                () -> recurso.consumoInsumos("2025-01-01", "2026-01-02"));
        for (Runnable caso : casos) {
            RuntimeException error = assertThrows(RuntimeException.class, caso::run);
            assertTrue(error instanceof BadRequestException
                    || error instanceof IllegalArgumentException, error.toString());
            try (Response respuesta = mapper.toResponse(error)) {
                assertEquals(400, respuesta.getStatus());
                assertInstanceOf(ApiError.class, respuesta.getEntity());
            }
        }
        assertTrue(fechas.isEmpty());
    }

    @Test
    void rangoDeMasDe366DiasSeRechazaConMensaje() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> recurso().pedidosPorSucursal("2026-01-01", "2027-01-02"));
        assertTrue(error.getMessage().contains("366"));
    }

    @Test
    void alertasUsanElUsuarioDeSesion() throws Exception {
        Method metodo = AlertaResource.class.getMethod("consultar", SecurityContext.class);
        assertEquals("/alertas", AlertaResource.class.getAnnotation(Path.class).value());
        assertEquals(6, metodo.getAnnotation(RolesAllowed.class).value().length);
        List<UsuarioSesion> usuarios = new ArrayList<>();
        AlertaResource recurso = new AlertaResource(new AlertaService() {
            @Override public AlertasDTO alertas(UsuarioSesion usuario) {
                usuarios.add(usuario);
                return new AlertasDTO(1, 2, 3);
            }
        });
        try (Response respuesta = recurso.consultar(SesionDePrueba.usuario(5, "GERENTE"))) {
            assertEquals(200, respuesta.getStatus());
            assertEquals(new AlertasDTO(1, 2, 3), respuesta.getEntity());
        }
        assertEquals(5, usuarios.get(0).getIdUsuario());
        assertEquals("GERENTE", usuarios.get(0).getRol());
    }
}
