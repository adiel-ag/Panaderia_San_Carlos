package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sancarlos.dto.IngredienteRecetaDTO;
import gt.edu.umg.sancarlos.dto.NuevaRecetaDTO;
import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.dto.RecetaDetalleDTO;
import gt.edu.umg.sancarlos.dto.RecetaResumenDTO;
import gt.edu.umg.sancarlos.dto.RecetaVersionDTO;
import gt.edu.umg.sancarlos.exception.RecetaConflictoException;
import gt.edu.umg.sancarlos.exception.RecetaNoEncontradaException;
import gt.edu.umg.sancarlos.exception.RecetaSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.RecetaService;
import jakarta.annotation.Priority;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecetaResourceTest {

    @Test
    void declaraGetParaConsultarRecetaActivaPorProducto() throws Exception {
        assertEquals("/recetas", RecetaResource.class.getAnnotation(Path.class).value());
        var method = RecetaResource.class.getMethod("buscarRecetaActiva", String.class);
        assertInstanceOf(GET.class, method.getAnnotation(GET.class));
        assertEquals("productos/{idProducto}/activa", method.getAnnotation(Path.class).value());
    }

    @Test
    void devuelve200ConElIdentificadorDeLaRecetaActiva() {
        RecetaService service = new RecetaService() {
            @Override
            public RecetaActivaDTO buscarRecetaActiva(long idProducto) {
                return new RecetaActivaDTO(idProducto, 88);
            }
        };

        try (Response response = new RecetaResource(service).buscarRecetaActiva("35")) {
            assertEquals(200, response.getStatus());
            RecetaActivaDTO body = assertInstanceOf(RecetaActivaDTO.class, response.getEntity());
            assertEquals(35, body.getIdProducto());
            assertEquals(88, body.getIdReceta());
        }
    }

    @Test
    void rechazaIdNoNumericoOCero() {
        RecetaResource resource = new RecetaResource(new RecetaService());

        assertThrows(BadRequestException.class, () -> resource.buscarRecetaActiva("abc"));
        assertThrows(BadRequestException.class, () -> resource.buscarRecetaActiva("0"));
        assertThrows(BadRequestException.class, () -> resource.buscarRecetaActiva("-4"));
    }

    @Test
    void declaraRolesDeLosEndpointsDeRecetas() throws Exception {
        Set<String> lectura = Set.of("ADMINISTRADOR", "GERENTE", "PRODUCCION");
        Method listar = RecetaResource.class.getMethod("listarRecetas");
        Method detalle = RecetaResource.class.getMethod("buscarReceta", String.class);
        Method versiones = RecetaResource.class.getMethod("listarVersiones", String.class);
        Method crear = RecetaResource.class.getMethod("crearReceta", NuevaRecetaDTO.class);

        assertNull(listar.getAnnotation(Path.class));
        assertEquals("{idReceta}", detalle.getAnnotation(Path.class).value());
        assertEquals("productos/{idProducto}/versiones",
                versiones.getAnnotation(Path.class).value());
        for (Method metodo : List.of(listar, detalle, versiones)) {
            assertNotNull(metodo.getAnnotation(GET.class), metodo.getName());
            assertEquals(lectura, Set.of(metodo.getAnnotation(RolesAllowed.class).value()),
                    metodo.getName());
        }
        assertNotNull(crear.getAnnotation(POST.class));
        assertEquals(Set.of("ADMINISTRADOR", "PRODUCCION"),
                Set.of(crear.getAnnotation(RolesAllowed.class).value()));
    }

    @Test
    void listaDetalleYVersionesResponden200() throws Exception {
        RecetaService servicio = new RecetaService() {
            @Override public List<RecetaResumenDTO> listar() {
                return List.of(new RecetaResumenDTO(33, "Pan", "UND", 9L, 2,
                        BigDecimal.TEN, 3, LocalDate.of(2026, 10, 31), 1),
                        new RecetaResumenDTO(34, "Pastel", "UND", null, null, null, 0, null, 0));
            }
            @Override public RecetaDetalleDTO buscarPorId(long idReceta) {
                return detalle(idReceta);
            }
            @Override public List<RecetaVersionDTO> listarVersiones(long idProducto) {
                return List.of(new RecetaVersionDTO(9, 2, BigDecimal.TEN, "ACTIVA",
                        LocalDate.of(2026, 10, 31), null, true, 3));
            }
        };

        ContainerResponse lista = llamar(servicio, "GET", "recetas", null);
        assertEquals(200, lista.getStatus());
        JsonNode filas = json(lista);
        assertEquals("2026-10-31", filas.get(0).get("fechaInicio").asText());
        assertEquals(1, filas.get(0).get("recetasVigentes").asInt());
        assertTrue(filas.get(1).get("idReceta").isNull());
        assertEquals(Set.of("idProducto", "nombreProducto", "unidad", "idReceta", "version",
                "rendimiento", "cantidadIngredientes", "fechaInicio", "recetasVigentes"),
                campos(filas.get(0)));

        ContainerResponse detalle = llamar(servicio, "GET", "recetas/9", null);
        assertEquals(200, detalle.getStatus());
        JsonNode cuerpo = json(detalle);
        assertTrue(cuerpo.get("vigente").asBoolean());
        assertEquals("Harina", cuerpo.get("ingredientes").get(0).get("nombre").asText());
        assertEquals(Set.of("idReceta", "idProducto", "nombreProducto", "unidad", "version",
                "rendimiento", "estado", "fechaInicio", "fechaFin", "vigente", "ingredientes"),
                campos(cuerpo));

        ContainerResponse versiones = llamar(servicio, "GET",
                "recetas/productos/33/versiones", null);
        assertEquals(200, versiones.getStatus());
        assertEquals(Set.of("idReceta", "version", "rendimiento", "estado", "fechaInicio",
                "fechaFin", "vigente", "cantidadIngredientes"), campos(json(versiones).get(0)));
    }

    @Test
    void crearResponde201ConLocationYLaRecetaNueva() throws Exception {
        List<NuevaRecetaDTO> recibidas = new ArrayList<>();
        RecetaService servicio = new RecetaService() {
            @Override public RecetaDetalleDTO crear(NuevaRecetaDTO solicitud) {
                recibidas.add(solicitud);
                return detalle(70);
            }
        };

        ContainerResponse respuesta = llamar(servicio, "POST", "recetas",
                "{\"idProducto\":33,\"rendimiento\":12.5,"
                + "\"ingredientes\":[{\"idInsumo\":2,\"cantidad\":1.250}]}");

        assertEquals(201, respuesta.getStatus());
        assertTrue(respuesta.getHeaderString("Location").endsWith("/recetas/70"),
                respuesta.getHeaderString("Location"));
        assertEquals(70, json(respuesta).get("idReceta").asLong());
        NuevaRecetaDTO recibida = recibidas.get(0);
        assertEquals(33L, recibida.idProducto());
        assertEquals(0, new BigDecimal("12.5").compareTo(recibida.rendimiento()));
        assertEquals(2L, recibida.ingredientes().get(0).idInsumo());

        try (Response directa = new RecetaResource(servicio).crearReceta(
                new NuevaRecetaDTO(33L, BigDecimal.ONE, List.of()))) {
            assertEquals("/recetas/70", directa.getLocation().toString());
        }
    }

    @Test
    void erroresDeRecetaSeTraducenA400404Y409() throws Exception {
        RecetaService servicio = new RecetaService() {
            @Override public RecetaDetalleDTO buscarPorId(long idReceta) {
                throw new RecetaNoEncontradaException(
                        "No existe una receta con ID " + idReceta, null);
            }
            @Override public List<RecetaVersionDTO> listarVersiones(long idProducto) {
                throw new RecetaNoEncontradaException(
                        "No existe un producto con ID " + idProducto, null);
            }
            @Override public RecetaDetalleDTO crear(NuevaRecetaDTO solicitud) {
                if (solicitud.idProducto() == 1) {
                    throw new RecetaSolicitudInvalidaException(
                            "El ingrediente #99 no existe", null);
                }
                throw new RecetaConflictoException(
                        "Solo los productos finales activos llevan receta", null);
            }
        };
        String resto = ",\"rendimiento\":1,\"ingredientes\":[{\"idInsumo\":99,\"cantidad\":1}]}";

        ContainerResponse noExiste = llamar(servicio, "GET", "recetas/5", null);
        assertEquals(404, noExiste.getStatus());
        assertEquals("No existe una receta con ID 5", json(noExiste).get("message").asText());
        assertEquals(404, llamar(servicio, "GET", "recetas/productos/8/versiones", null)
                .getStatus());

        ContainerResponse invalida = llamar(servicio, "POST", "recetas",
                "{\"idProducto\":1" + resto);
        assertEquals(400, invalida.getStatus());
        assertEquals("El ingrediente #99 no existe", json(invalida).get("message").asText());

        ContainerResponse conflicto = llamar(servicio, "POST", "recetas",
                "{\"idProducto\":2" + resto);
        assertEquals(409, conflicto.getStatus());
        assertEquals("Solo los productos finales activos llevan receta",
                json(conflicto).get("message").asText());

        assertEquals(400, llamar(servicio, "GET", "recetas/abc", null).getStatus());
        assertEquals(400, llamar(servicio, "GET", "recetas/productos/0/versiones", null)
                .getStatus());
        assertEquals(400, llamar(servicio, "POST", "recetas", "{\"otro\":1}").getStatus());
    }

    @Test
    void cuerpoVacioResponde400SinAbrirConexion() throws Exception {
        ContainerResponse respuesta = llamar(new RecetaService(), "POST", "recetas", "");
        assertEquals(400, respuesta.getStatus());
        assertEquals("Los datos de la receta son obligatorios",
                json(respuesta).get("message").asText());
    }

    private static RecetaDetalleDTO detalle(long idReceta) {
        return new RecetaDetalleDTO(idReceta, 33, "Pan", "UND", 2, BigDecimal.TEN, "ACTIVA",
                LocalDate.of(2026, 10, 31), null, true,
                List.of(new IngredienteRecetaDTO(2, "Harina", "KG", new BigDecimal("1.250"))));
    }

    private static ContainerResponse llamar(RecetaService servicio, String metodo, String ruta,
            String json) throws Exception {
        ApplicationHandler handler = new ApplicationHandler(new ResourceConfig()
                .register(new RecetaResource(servicio))
                .register(new SesionProduccion())
                .register(AutorizacionFeature.class)
                .register(JacksonObjectMapperProvider.class)
                .register(ApiExceptionMapper.class));
        URI base = URI.create("http://localhost/api/");
        ContainerRequest request = new ContainerRequest(base, base.resolve(ruta), metodo, null,
                new MapPropertiesDelegate(), null);
        if (json != null) {
            request.getHeaders().putSingle("Content-Type", "application/json");
            request.setEntityStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ContainerResponse respuesta = handler.apply(request, salida).get();
        respuesta.setEntity(salida.toString(StandardCharsets.UTF_8));
        return respuesta;
    }

    private static JsonNode json(ContainerResponse respuesta) throws Exception {
        return new ObjectMapper().readTree((String) respuesta.getEntity());
    }

    private static Set<String> campos(JsonNode nodo) {
        Set<String> nombres = new TreeSet<>();
        nodo.fieldNames().forEachRemaining(nombres::add);
        return nombres;
    }

    /** Hace de SesionFilter con un usuario de produccion. */
    @Priority(Priorities.AUTHENTICATION)
    private static final class SesionProduccion implements ContainerRequestFilter {
        @Override public void filter(ContainerRequestContext contexto) {
            contexto.setSecurityContext(new UsuarioSecurityContext(new UsuarioSesion(
                    1, "prod", "Produccion", "PRODUCCION", null), false));
        }
    }
}
