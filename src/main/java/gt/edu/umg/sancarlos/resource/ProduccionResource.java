package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.AsociarPedidoDTO;
import gt.edu.umg.sancarlos.dto.CantidadProducidaDTO;
import gt.edu.umg.sancarlos.dto.CrearProduccionDTO;
import gt.edu.umg.sancarlos.dto.PlanificarProduccionDTO;
import gt.edu.umg.sancarlos.dto.ProduccionCreadaDTO;
import gt.edu.umg.sancarlos.dto.ProduccionOperacionDTO;
import gt.edu.umg.sancarlos.service.ProduccionService;
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
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;

/** Endpoints REST de producción; las reglas de negocio las resuelve PKG_PRODUCCION. */
@Path("/producciones")
@Produces(MediaType.APPLICATION_JSON)
public class ProduccionResource {

    private final ProduccionService produccionService;

    public ProduccionResource() {
        this(new ProduccionService());
    }

    ProduccionResource(ProduccionService produccionService) {
        this.produccionService = produccionService;
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response listarProducciones(@QueryParam("pagina") String pagina,
            @QueryParam("tamano") String tamano, @QueryParam("estado") String estado) {
        Paginacion paginacion = Paginacion.de(pagina, tamano);
        ProduccionService.validarEstado(estado);
        return Response.ok(produccionService.listar(paginacion.pagina(),
                paginacion.tamano(), estado)).build();
    }

    @GET
    @Path("{id}")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response buscarProduccionPorId(@PathParam("id") String id) {
        return Response.ok(produccionService.buscarPorId(parsearId(id, "producción"))).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response crearProduccion(CrearProduccionDTO solicitud, @Context UriInfo uriInfo,
            @Context SecurityContext seguridad) {
        requerirCuerpo(solicitud);
        long idProduccion = produccionService.crearProduccion(
                UsuarioActual.id(seguridad), solicitud.getObservacion());
        return Response.created(uriInfo.getAbsolutePathBuilder()
                        .path(Long.toString(idProduccion)).build())
                .entity(new ProduccionCreadaDTO(idProduccion))
                .build();
    }

    @GET
    @Path("pedidos-disponibles")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response pedidosDisponibles(@QueryParam("fechaEntrega") String fechaEntrega) {
        try {
            return Response.ok(produccionService.pedidosDisponibles(LocalDate.parse(fechaEntrega))).build();
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new BadRequestException("fechaEntrega debe tener el formato yyyy-MM-dd.");
        }
    }

    @POST
    @Path("planificar")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response planificar(PlanificarProduccionDTO solicitud, @Context UriInfo uriInfo,
            @Context SecurityContext seguridad) {
        requerirCuerpo(solicitud);
        if (solicitud.fechaEntrega() == null) {
            throw new BadRequestException("La fecha de entrega es obligatoria.");
        }
        if (solicitud.idPedidos() == null || solicitud.idPedidos().isEmpty()
                || solicitud.idPedidos().size() > 100
                || solicitud.idPedidos().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(solicitud.idPedidos()).size() != solicitud.idPedidos().size()) {
            throw new BadRequestException("Seleccione entre 1 y 100 pedidos distintos.");
        }
        long idProduccion = produccionService.crearYGenerarPlan(UsuarioActual.id(seguridad),
                solicitud.observacion(), solicitud.idPedidos());
        return Response.created(uriInfo.getBaseUriBuilder().path("producciones")
                        .path(Long.toString(idProduccion)).build())
                .entity(new ProduccionCreadaDTO(idProduccion)).build();
    }

    @POST
    @Path("{id}/pedidos")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response asociarPedido(@PathParam("id") String id, AsociarPedidoDTO solicitud) {
        long idProduccion = parsearId(id, "producción");
        requerirCuerpo(solicitud);
        produccionService.asociarPedido(idProduccion, solicitud.getIdPedido());
        return Response.ok(new ProduccionOperacionDTO(idProduccion,
                "Pedido " + solicitud.getIdPedido() + " asociado a la producción")).build();
    }

    /** Genera el plan en Oracle y devuelve los renglones que este calculó. */
    @POST
    @Path("{id}/generar-plan")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response generarPlan(@PathParam("id") String id) {
        long idProduccion = parsearId(id, "producción");
        produccionService.generarPlan(idProduccion);
        return Response.ok(produccionService.consultarDetalle(idProduccion)).build();
    }

    @GET
    @Path("{id}/detalle")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response consultarDetalle(@PathParam("id") String id) {
        long idProduccion = parsearId(id, "producción");
        return Response.ok(produccionService.consultarDetalle(idProduccion)).build();
    }

    @GET
    @Path("{id}/insumos")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION})
    public Response consultarInsumos(@PathParam("id") String id) {
        return Response.ok(produccionService.consultarInsumos(parsearId(id, "producción"))).build();
    }

    /** Reemplaza la cantidad producida del producto; no la acumula. */
    @PUT
    @Path("{id}/productos/{idProducto}/cantidad-producida")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response registrarCantidadProducida(@PathParam("id") String id,
            @PathParam("idProducto") String idProducto, CantidadProducidaDTO solicitud) {
        long idProduccion = parsearId(id, "producción");
        long producto = parsearId(idProducto, "producto");
        requerirCuerpo(solicitud);
        if (solicitud.getCantidadProducida() == null
                || solicitud.getCantidadProducida().signum() <= 0) {
            throw new BadRequestException("La cantidad producida debe ser mayor que cero.");
        }
        produccionService.registrarProducido(idProduccion, producto,
                solicitud.getCantidadProducida());
        return Response.ok(new ProduccionOperacionDTO(idProduccion,
                "Cantidad producida registrada para el producto " + producto)).build();
    }

    /** No necesita cuerpo: finaliza el usuario de la sesion; un cuerpo enviado se ignora. */
    @POST
    @Path("{id}/finalizar")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response finalizarProduccion(@PathParam("id") String id,
            @Context SecurityContext seguridad) {
        long idProduccion = parsearId(id, "producción");
        produccionService.finalizarProduccion(idProduccion, UsuarioActual.id(seguridad));
        return Response.ok(new ProduccionOperacionDTO(idProduccion,
                "Producción finalizada")).build();
    }

    /** Solo una producción planificada; sus pedidos quedan libres para otra. */
    @POST
    @Path("{id}/cancelar")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.PRODUCCION})
    public Response cancelarProduccion(@PathParam("id") String id) {
        long idProduccion = parsearId(id, "producción");
        return Response.ok(produccionService.cancelarProduccion(idProduccion)).build();
    }

    private static long parsearId(String valor, String nombre) {
        final long id;
        try {
            id = Long.parseLong(valor);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID de " + nombre + " debe ser un número entero positivo.");
        }
        if (id <= 0) {
            throw new BadRequestException("El ID de " + nombre + " debe ser un número entero positivo.");
        }
        return id;
    }

    private static void requerirCuerpo(Object solicitud) {
        if (solicitud == null) {
            throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        }
    }
}
