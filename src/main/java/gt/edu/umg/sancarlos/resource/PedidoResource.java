package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.CrearPedidoDTO;
import gt.edu.umg.sancarlos.dto.EditarPedidoDTO;
import gt.edu.umg.sancarlos.dto.RecepcionPedidoDTO;
import gt.edu.umg.sancarlos.service.DespachoService;
import gt.edu.umg.sancarlos.dto.PedidoCreadoDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.PedidoResumenDTO;
import gt.edu.umg.sancarlos.model.Pedido;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.PedidoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.Optional;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Endpoints REST para crear y consultar pedidos. */
@Path("/pedidos")
@Produces(MediaType.APPLICATION_JSON)
public class PedidoResource {

    private final PedidoService pedidoService;
    private final DespachoService despachoService;

    public PedidoResource() {
        this(new PedidoService());
    }

    PedidoResource(PedidoService pedidoService) {
        this(pedidoService, new DespachoService());
    }

    PedidoResource(PedidoService pedidoService, DespachoService despachoService) {
        this.pedidoService = pedidoService;
        this.despachoService = despachoService;
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.DESPACHO})
    public Response listarPedidos(@QueryParam("pagina") String pagina,
            @QueryParam("tamano") String tamano, @QueryParam("estado") String estado,
            @QueryParam("idSucursal") String idSucursal,
            @QueryParam("entregaDesde") String entregaDesde,
            @QueryParam("entregaHasta") String entregaHasta,
            @Context SecurityContext seguridad) {
        Paginacion paginacion = Paginacion.de(pagina, tamano);
        Long sucursal = sucursal(idSucursal);
        LocalDate desde = fecha(entregaDesde, "entregaDesde");
        LocalDate hasta = fecha(entregaHasta, "entregaHasta");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BadRequestException("entregaDesde debe ser anterior o igual a entregaHasta.");
        }
        PedidoService.validarEstado(estado);
        UsuarioSesion usuario = UsuarioActual.de(seguridad);
        if (!AlcanceSucursal.puedeListar(usuario, sucursal)) {
            return Response.ok(new Pagina<PedidoResumenDTO>(java.util.List.of(), 0,
                    paginacion.pagina(), paginacion.tamano())).build();
        }
        return Response.ok(pedidoService.listar(paginacion.pagina(), paginacion.tamano(),
                estado, AlcanceSucursal.sucursalEfectiva(usuario, sucursal), desde, hasta)).build();
    }

    @POST
    @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.SUCURSAL})
    public Response crearPedido(CrearPedidoDTO solicitud, @Context UriInfo uriInfo,
            @Context SecurityContext seguridad) {
        if (solicitud == null) {
            throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        }
        // El pedido se registra a nombre del usuario de la sesion, nunca del JSON.
        solicitud.setIdUsuario(UsuarioActual.id(seguridad));
        long idPedido = pedidoService.crearPedido(solicitud);
        return Response.created(uriInfo.getAbsolutePathBuilder()
                        .path(Long.toString(idPedido)).build())
                .entity(new PedidoCreadoDTO(idPedido))
                .build();
    }

    @GET
    @Path("{id}")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.DESPACHO})
    public Response buscarPedidoPorId(@PathParam("id") String id,
            @Context SecurityContext seguridad) {
        long idPedido = parsearId(id);
        Optional<Pedido> pedido = pedidoService.buscarPedidoPorId(idPedido);
        Pedido encontrado = pedido.orElseThrow(() -> new NotFoundException(
                "No existe un pedido con ID " + idPedido));
        if (!AlcanceSucursal.puedeVer(UsuarioActual.de(seguridad), encontrado.getIdSucursal())) {
            throw new NotFoundException("No existe un pedido con ID " + idPedido);
        }
        return Response.ok(encontrado).build();
    }

    /** Cancela un pedido pendiente o confirmado que aun no esta en una produccion. */
    @POST
    @Path("{id}/cancelar")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.SUCURSAL})
    public Response cancelarPedido(@PathParam("id") String id,
            @Context SecurityContext seguridad) {
        long idPedido = parsearId(id);
        return Response.ok(pedidoService.cancelarPedido(idPedido,
                UsuarioActual.de(seguridad))).build();
    }

    @PUT
    @Path("{id}")
    @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.SUCURSAL})
    public Response editarPedido(@PathParam("id") String id, EditarPedidoDTO solicitud,
            @Context SecurityContext seguridad) {
        if (solicitud == null) throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        return Response.ok(pedidoService.editarPedido(parsearId(id), solicitud,
                UsuarioActual.de(seguridad))).build();
    }

    @POST
    @Path("{id}/recepcion")
    @jakarta.ws.rs.Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.SUCURSAL})
    public Response recibirPedido(@PathParam("id") String id, RecepcionPedidoDTO solicitud,
            @Context SecurityContext seguridad) {
        return Response.ok(despachoService.recibirPedido(parsearId(id),
                UsuarioActual.de(seguridad), solicitud == null ? null : solicitud.observacion())).build();
    }

    private static long parsearId(String id) {
        final long idPedido;
        try {
            idPedido = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del pedido debe ser un número entero positivo.");
        }
        if (idPedido <= 0) {
            throw new BadRequestException("El ID del pedido debe ser un número entero positivo.");
        }
        return idPedido;
    }

    private static Long sucursal(String texto) {
        if (texto == null) return null;
        try {
            long valor = Long.parseLong(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException exception) {
            // Se informa el mismo error para todos los valores invalidos.
        }
        throw new BadRequestException("El ID de sucursal debe ser positivo.");
    }

    private static LocalDate fecha(String texto, String nombre) {
        if (texto == null) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(nombre + " debe tener el formato yyyy-MM-dd.");
        }
    }
}

