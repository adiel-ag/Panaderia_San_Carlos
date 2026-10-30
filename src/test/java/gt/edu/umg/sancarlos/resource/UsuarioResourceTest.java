package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.AsignarClaveDTO;
import gt.edu.umg.sancarlos.dto.MensajeDTO;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioResourceTest {

    @Test
    void asignarClaveEsSoloParaAdministrador() throws Exception {
        Method metodo = UsuarioResource.class.getMethod("asignarClave", String.class, AsignarClaveDTO.class);
        assertEquals("/usuarios", UsuarioResource.class.getAnnotation(Path.class).value());
        assertEquals("{id}/clave", metodo.getAnnotation(Path.class).value());
        assertNotNull(metodo.getAnnotation(PUT.class));
        assertArrayEquals(new String[]{"ADMINISTRADOR"}, metodo.getAnnotation(RolesAllowed.class).value());
    }

    @Test
    void asignaLaClaveYLaBorraDeMemoria() {
        List<char[]> recibidas = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        AutenticacionService service = new AutenticacionService() {
            @Override
            public void establecerClave(long idUsuario, char[] clave) {
                ids.add(idUsuario);
                recibidas.add(clave);
            }
        };

        try (Response response = new UsuarioResource(service).asignarClave("7", clave("clave-nueva-1"))) {
            assertEquals(200, response.getStatus());
            assertEquals("Contraseña asignada", ((MensajeDTO) response.getEntity()).getMensaje());
        }
        assertEquals(List.of(7L), ids);
        assertArrayEquals(new char[13], recibidas.get(0));
    }

    @Test
    void rechazaIdYCuerpoInvalidosSinLlamarAlServicio() {
        List<Long> ids = new ArrayList<>();
        AutenticacionService service = new AutenticacionService() {
            @Override
            public void establecerClave(long idUsuario, char[] clave) {
                ids.add(idUsuario);
            }
        };
        UsuarioResource resource = new UsuarioResource(service);

        assertThrows(BadRequestException.class, () -> resource.asignarClave("abc", clave("clave-nueva-1")));
        assertThrows(BadRequestException.class, () -> resource.asignarClave("0", clave("clave-nueva-1")));
        assertThrows(BadRequestException.class, () -> resource.asignarClave("7", null));
        assertThrows(BadRequestException.class, () -> resource.asignarClave("7", new AsignarClaveDTO()));
        assertTrue(ids.isEmpty());
    }

    private static AsignarClaveDTO clave(String valor) {
        AsignarClaveDTO dto = new AsignarClaveDTO();
        dto.setPassword(valor);
        return dto;
    }
}
