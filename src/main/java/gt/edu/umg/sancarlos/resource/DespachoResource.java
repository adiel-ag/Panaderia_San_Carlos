package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.DespacharPedidoDTO;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.service.DespachoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/** Endpoints REST del despacho de un pedido; las reglas las resuelve PKG_DESPACHO. */
@Path("/pedidos/{id}/despacho")
@Produces(MediaType.APPLICATION_JSON)
public class DespachoResource {

    private final DespachoService despachoService;

    public DespachoResource() {
        this(new DespachoService());
    }

    DespachoResource(DespachoService despachoService) {
        this.despachoService = despachoService;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.DESPACHO})
    public Response despacharPedido(@PathParam("id") String id, DespacharPedidoDTO solicitud,
            @Context UriInfo uriInfo) {
        long idPedido = parsearIdPedido(id);
        if (solicitud == null) {
            throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        }
        Despacho despacho = despachoService.despacharPedido(
                idPedido, solicitud.getIdUsuario(), solicitud.getObservacion());
        return Response.created(uriInfo.getAbsolutePath()).entity(despacho).build();
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL, Roles.DESPACHO})
    public Response buscarDespacho(@PathParam("id") String id) {
        long idPedido = parsearIdPedido(id);
        Despacho despacho = despachoService.buscarPorPedido(idPedido)
                .orElseThrow(() -> new NotFoundException(
                        "No existe un despacho para el pedido " + idPedido));
        return Response.ok(despacho).build();
    }

    private static long parsearIdPedido(String valor) {
        final long id;
        try {
            id = Long.parseLong(valor);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del pedido debe ser un número entero positivo.");
        }
        if (id <= 0) {
            throw new BadRequestException("El ID del pedido debe ser un número entero positivo.");
        }
        return id;
    }
}
