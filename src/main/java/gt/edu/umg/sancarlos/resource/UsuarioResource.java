package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.AsignarClaveDTO;
import gt.edu.umg.sancarlos.dto.ActualizarUsuarioDTO;
import gt.edu.umg.sancarlos.dto.CambiarEstadoDTO;
import gt.edu.umg.sancarlos.dto.CrearUsuarioDTO;
import gt.edu.umg.sancarlos.dto.MensajeDTO;
import gt.edu.umg.sancarlos.dto.UsuarioDTO;
import gt.edu.umg.sancarlos.service.AutenticacionService;
import gt.edu.umg.sancarlos.service.UsuarioService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.net.URI;

/** Administracion de usuarios y asignacion de claves. */
@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    private final AutenticacionService autenticacionService;
    private final UsuarioService usuarioService;

    public UsuarioResource() {
        this(new AutenticacionService(), new UsuarioService());
    }

    UsuarioResource(AutenticacionService autenticacionService) {
        this(autenticacionService, new UsuarioService());
    }

    UsuarioResource(AutenticacionService autenticacionService, UsuarioService usuarioService) {
        this.autenticacionService = autenticacionService;
        this.usuarioService = usuarioService;
    }

    @GET
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response listarUsuarios(@QueryParam("pagina") String pagina,
            @QueryParam("tamano") String tamano, @QueryParam("rol") String rol,
            @QueryParam("estado") String estado, @QueryParam("idSucursal") String idSucursal) {
        int numeroPagina = entero(pagina, 1, "La página debe ser un entero positivo");
        int numeroTamano = entero(tamano, 20, "El tamaño debe ser un entero entre 1 y 100");
        Long sucursal = idSucursal == null || idSucursal.isBlank() ? null
                : positivo(idSucursal, "El ID de sucursal debe ser positivo");
        return Response.ok(usuarioService.listar(numeroPagina, numeroTamano,
                vacioANull(rol), vacioANull(estado), sucursal)).build();
    }

    @GET
    @Path("{id}")
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response buscarUsuario(@PathParam("id") String id) {
        return Response.ok(usuarioService.buscarPorId(id(id))).build();
    }

    @POST
    @RolesAllowed(Roles.ADMINISTRADOR)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crearUsuario(CrearUsuarioDTO solicitud) {
        char[] clave = solicitud == null || solicitud.password() == null
                ? null : solicitud.password().toCharArray();
        try {
            UsuarioDTO creado = usuarioService.crear(solicitud, clave);
            return Response.created(URI.create("/usuarios/" + creado.idUsuario()))
                    .entity(creado).build();
        } finally {
            if (clave != null) Arrays.fill(clave, '\0');
        }
    }

    @PUT
    @Path("{id}")
    @RolesAllowed(Roles.ADMINISTRADOR)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response actualizarUsuario(@PathParam("id") String id,
            ActualizarUsuarioDTO solicitud) {
        return Response.ok(usuarioService.actualizar(id(id), solicitud)).build();
    }

    @PUT
    @Path("{id}/estado")
    @RolesAllowed(Roles.ADMINISTRADOR)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cambiarEstado(@PathParam("id") String id, CambiarEstadoDTO solicitud) {
        return Response.ok(usuarioService.cambiarEstado(id(id),
                solicitud == null ? null : solicitud.estado())).build();
    }

    @POST
    @Path("{id}/desbloquear")
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response desbloquear(@PathParam("id") String id) {
        return Response.ok(usuarioService.desbloquear(id(id))).build();
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

    private static long id(String texto) {
        try {
            long valor = Long.parseLong(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException exception) {
            // Se usa el mismo mensaje para todos los IDs invalidos.
        }
        throw new BadRequestException("El ID del usuario debe ser un número entero positivo.");
    }

    private static int entero(String texto, int predeterminado, String mensaje) {
        if (texto == null) return predeterminado;
        try {
            int valor = Integer.parseInt(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException exception) {
            // Se informa un error de solicitud sin detalles internos.
        }
        throw new BadRequestException(mensaje);
    }

    private static long positivo(String texto, String mensaje) {
        try {
            long valor = Long.parseLong(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException exception) {
            // Se informa un error de solicitud sin detalles internos.
        }
        throw new BadRequestException(mensaje);
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
