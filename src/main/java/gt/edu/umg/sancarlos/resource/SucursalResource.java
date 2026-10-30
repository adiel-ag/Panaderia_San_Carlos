package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.service.CatalogoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Consulta de sucursales activas. */
@Path("/sucursales")
@Produces(MediaType.APPLICATION_JSON)
public class SucursalResource {
    private final CatalogoService catalogoService;

    public SucursalResource() {
        this(new CatalogoService());
    }

    SucursalResource(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response listarSucursales() {
        return Response.ok(catalogoService.sucursales()).build();
    }
}
