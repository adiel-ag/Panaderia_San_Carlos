package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.LoginDTO;
import gt.edu.umg.sancarlos.dto.CambiarClaveDTO;
import gt.edu.umg.sancarlos.dto.MensajeDTO;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import gt.edu.umg.sancarlos.service.AutenticacionService.SesionIniciada;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.annotation.security.RolesAllowed;
import java.util.Arrays;

/** Inicio y cierre de sesion; la sesion se guarda en una cookie HttpOnly. */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    private final AutenticacionService autenticacionService;

    public AuthResource() {
        this(new AutenticacionService());
    }

    AuthResource(AutenticacionService autenticacionService) {
        this.autenticacionService = autenticacionService;
    }

    @POST
    @Path("login")
    @PermitAll
    @SinRevalidarSesion
    @Consumes(MediaType.APPLICATION_JSON)
    public Response iniciarSesion(LoginDTO solicitud, @Context HttpServletRequest request) {
        if (solicitud == null) {
            throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        }
        char[] clave = solicitud.getPassword() == null
                ? null : solicitud.getPassword().toCharArray();
        try {
            SesionIniciada sesion = autenticacionService.iniciarSesion(
                    solicitud.getUsername(), clave);
            SesionUsuario.iniciar(request, sesion);
            return Response.ok(sesion.usuario()).build();
        } finally {
            if (clave != null) {
                Arrays.fill(clave, '\0');
            }
        }
    }

    /** No consulta Oracle: cierra la sesion aunque la base falle o tarde. */
    @POST
    @Path("logout")
    @PermitAll
    @SinRevalidarSesion
    public Response cerrarSesion(@Context HttpServletRequest request) {
        SesionUsuario.cerrar(request);
        return Response.ok(new MensajeDTO("Sesión cerrada")).build();
    }

    @GET
    @Path("me")
    @PermitAll
    public Response usuarioActual(@Context SecurityContext seguridad) {
        return Response.ok(UsuarioActual.de(seguridad)).build();
    }

    @PUT
    @Path("clave")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cambiarClave(CambiarClaveDTO solicitud,
            @Context SecurityContext seguridad, @Context HttpServletRequest request) {
        var usuario = UsuarioActual.de(seguridad);
        if (solicitud == null) {
            throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        }
        char[] actual = solicitud.claveActual() == null ? null : solicitud.claveActual().toCharArray();
        char[] nueva = solicitud.claveNueva() == null ? null : solicitud.claveNueva().toCharArray();
        try {
            SesionIniciada sesion = autenticacionService.cambiarClave(usuario, actual, nueva);
            SesionUsuario.iniciar(request, sesion);
            return Response.ok(new MensajeDTO("Contraseña actualizada")).build();
        } finally {
            if (actual != null) Arrays.fill(actual, '\0');
            if (nueva != null) Arrays.fill(nueva, '\0');
        }
    }
}
