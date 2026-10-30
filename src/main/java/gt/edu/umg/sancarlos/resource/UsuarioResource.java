package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.AsignarClaveDTO;
import gt.edu.umg.sancarlos.dto.MensajeDTO;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;

/** Administracion de usuarios; por ahora solo la asignacion de claves. */
@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    private final AutenticacionService autenticacionService;

    public UsuarioResource() {
        this(new AutenticacionService());
    }

    UsuarioResource(AutenticacionService autenticacionService) {
        this.autenticacionService = autenticacionService;
    }

    /** Las sesiones abiertas del usuario dejan de valer al cambiar su clave. */
    @PUT
    @Path("{id}/clave")
    @RolesAllowed(Roles.ADMINISTRADOR)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response asignarClave(@PathParam("id") String id, AsignarClaveDTO solicitud) {
        final long idUsuario;
        try {
            idUsuario = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del usuario debe ser un número entero positivo.");
        }
        if (idUsuario <= 0) {
            throw new BadRequestException("El ID del usuario debe ser un número entero positivo.");
        }
        if (solicitud == null || solicitud.getPassword() == null) {
            throw new BadRequestException("La contraseña es obligatoria.");
        }
        char[] clave = solicitud.getPassword().toCharArray();
        try {
            autenticacionService.establecerClave(idUsuario, clave);
        } finally {
            Arrays.fill(clave, '\0');
        }
        return Response.ok(new MensajeDTO("Contraseña asignada")).build();
    }
}
