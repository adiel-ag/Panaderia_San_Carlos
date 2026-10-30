package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.service.RecetaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Endpoints REST de consulta de recetas. */
@Path("/recetas")
@Produces(MediaType.APPLICATION_JSON)
public class RecetaResource {

    private final RecetaService recetaService;

    public RecetaResource() {
        this(new RecetaService());
    }

    RecetaResource(RecetaService recetaService) {
        this.recetaService = recetaService;
    }

    @GET
    @Path("productos/{idProducto}/activa")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response buscarRecetaActiva(@PathParam("idProducto") String idProducto) {
        final long id;
        try {
            id = Long.parseLong(idProducto);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
        }
        if (id <= 0) {
            throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
        }

        RecetaActivaDTO receta = recetaService.buscarRecetaActiva(id);
        return Response.ok(receta).build();
    }
}
