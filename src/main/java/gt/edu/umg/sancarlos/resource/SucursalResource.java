package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.service.CatalogoService;
import gt.edu.umg.sancarlos.service.SucursalAdminService;
import gt.edu.umg.sancarlos.dto.SucursalSolicitudDTO;
import gt.edu.umg.sancarlos.dto.EstadoCatalogoDTO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/** Consulta de sucursales activas. */
@Path("/sucursales")
@Produces(MediaType.APPLICATION_JSON)
public class SucursalResource {
    private final CatalogoService catalogoService;
    private final SucursalAdminService adminService;

    public SucursalResource() {
        this(new CatalogoService(), new SucursalAdminService());
    }

    SucursalResource(CatalogoService catalogoService) {
        this(catalogoService, new SucursalAdminService());
    }

    SucursalResource(CatalogoService catalogoService, SucursalAdminService adminService) {
        this.catalogoService = catalogoService;
        this.adminService = adminService;
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response listarSucursales() {
        return Response.ok(catalogoService.sucursales()).build();
    }

    @GET @Path("todas")
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response listarTodas() { return Response.ok(adminService.listar()).build(); }

    @POST @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response crearSucursal(SucursalSolicitudDTO solicitud) {
        var creada = adminService.crear(solicitud);
        return Response.created(URI.create("/sucursales/" + creada.idSucursal())).entity(creada).build();
    }

    @PUT @Path("{id}") @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response actualizarSucursal(@PathParam("id") String id, SucursalSolicitudDTO solicitud) {
        return Response.ok(adminService.actualizar(parsearId(id), solicitud)).build();
    }

    @PUT @Path("{id}/estado") @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed(Roles.ADMINISTRADOR)
    public Response cambiarEstado(@PathParam("id") String id, EstadoCatalogoDTO solicitud) {
        return Response.ok(adminService.cambiarEstado(parsearId(id),
                solicitud == null ? null : solicitud.estado())).build();
    }

    private static long parsearId(String texto) {
        try {
            long id = Long.parseLong(texto);
            if (id > 0) return id;
        } catch (NumberFormatException exception) {
            // Mismo mensaje para todos los ID inválidos.
        }
        throw new BadRequestException("El ID de la sucursal debe ser un número entero positivo.");
    }
}
