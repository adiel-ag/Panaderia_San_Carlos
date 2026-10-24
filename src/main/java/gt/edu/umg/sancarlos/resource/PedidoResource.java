package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.PedidoCreadoDTO;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.service.PedidoService;
import jakarta.ws.rs.BadRequestException;
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
import java.util.Optional;

/** Endpoints REST para crear y consultar pedidos. */
@Path("/pedidos")
@Produces(MediaType.APPLICATION_JSON)
public class PedidoResource {

    private final PedidoService pedidoService;

    public PedidoResource() {
        this(new PedidoService());
    }

    PedidoResource(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @POST
    @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
    public Response crearPedido(CrearPedidoDTO solicitud, @Context UriInfo uriInfo) {
        long idPedido = pedidoService.crearPedido(solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder()
                        .path(Long.toString(idPedido)).build())
                .entity(new PedidoCreadoDTO(idPedido))
                .build();
    }

    @GET
    @Path("{id}")
    public Response buscarPedidoPorId(@PathParam("id") String id) {
        final long idPedido;
        try {
            idPedido = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del pedido debe ser un número entero positivo.");
        }
        if (idPedido <= 0) {
            throw new BadRequestException("El ID del pedido debe ser un número entero positivo.");
        }

        Optional<Pedido> pedido = pedidoService.buscarPedidoPorId(idPedido);
        return Response.ok(pedido.orElseThrow(() -> new NotFoundException(
                "No existe un pedido con ID " + idPedido))).build();
    }
}

