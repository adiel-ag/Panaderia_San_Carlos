package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.NuevaRecetaDTO;
import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.dto.RecetaDetalleDTO;
import gt.edu.umg.sancarlos.service.RecetaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * Endpoints REST de recetas. Guardar una receta crea una version nueva; las
 * anteriores no se editan porque las producciones apuntan a ellas.
 */
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
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response listarRecetas() {
        return Response.ok(recetaService.listar()).build();
    }

    @GET
    @Path("{idReceta}")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response buscarReceta(@PathParam("idReceta") String idReceta) {
        return Response.ok(recetaService.buscarPorId(
                parsearId(idReceta, "El ID de la receta debe ser un número entero positivo."))).build();
    }

    @GET
    @Path("productos/{idProducto}/versiones")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response listarVersiones(@PathParam("idProducto") String idProducto) {
        return Response.ok(recetaService.listarVersiones(parsearId(idProducto,
                "El ID del producto debe ser un número entero positivo."))).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response crearReceta(NuevaRecetaDTO solicitud) {
        RecetaDetalleDTO creada = recetaService.crear(solicitud);
        return Response.created(URI.create("/recetas/" + creada.idReceta()))
                .entity(creada).build();
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

    private static long parsearId(String texto, String mensaje) {
        try {
            long valor = Long.parseLong(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException exception) {
            // Se usa el mismo mensaje para todos los IDs invalidos.
        }
        throw new BadRequestException(mensaje);
    }
}
