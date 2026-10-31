package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.service.CatalogoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/catalogo")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogoResource {
    private final CatalogoService service;

    public CatalogoResource() { this(new CatalogoService()); }
    CatalogoResource(CatalogoService service) { this.service = service; }

    @GET @Path("unidades")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response unidades() { return Response.ok(service.unidades()).build(); }

    @GET @Path("tipos-producto")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response tiposProducto() { return Response.ok(service.tiposProducto()).build(); }
}
