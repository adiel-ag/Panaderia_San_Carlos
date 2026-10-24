package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.service.RecetaService;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
