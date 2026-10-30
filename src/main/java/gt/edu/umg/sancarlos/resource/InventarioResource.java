package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.MovimientoInventarioDTO;
import gt.edu.umg.sancarlos.service.InventarioService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/** Endpoints REST de inventario; existencias y movimientos los resuelve PKG_INVENTARIO. */
@Path("/inventario")
@Produces(MediaType.APPLICATION_JSON)
public class InventarioResource {

    private final InventarioService inventarioService;

    public InventarioResource() {
        this(new InventarioService());
    }

    InventarioResource(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GET
    @Path("productos/{idProducto}/existencia")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.PRODUCCION,
            Roles.BODEGA, Roles.DESPACHO})
    public Response consultarExistencia(@PathParam("idProducto") String idProducto) {
        long id = parsearIdProducto(idProducto);
        return Response.ok(inventarioService.consultarExistencia(id)).build();
    }

    /** Entrada manual con origen AJUSTE; responde con la existencia resultante. */
    @POST
    @Path("productos/{idProducto}/entradas")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.BODEGA})
    public Response registrarEntrada(@PathParam("idProducto") String idProducto,
            MovimientoInventarioDTO solicitud, @Context SecurityContext seguridad) {
        long id = parsearIdProducto(idProducto);
        validarSolicitud(solicitud);
        return Response.ok(inventarioService.registrarEntrada(id, UsuarioActual.id(seguridad),
                solicitud.getCantidad(), solicitud.getObservacion())).build();
    }

    /** Salida manual con origen AJUSTE; responde con la existencia resultante. */
    @POST
    @Path("productos/{idProducto}/salidas")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.BODEGA})
    public Response registrarSalida(@PathParam("idProducto") String idProducto,
            MovimientoInventarioDTO solicitud, @Context SecurityContext seguridad) {
        long id = parsearIdProducto(idProducto);
        validarSolicitud(solicitud);
        return Response.ok(inventarioService.registrarSalida(id, UsuarioActual.id(seguridad),
                solicitud.getCantidad(), solicitud.getObservacion())).build();
    }

    private static long parsearIdProducto(String valor) {
        final long id;
        try {
            id = Long.parseLong(valor);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
        }
        if (id <= 0) {
            throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
        }
        return id;
    }

    private static void validarSolicitud(MovimientoInventarioDTO solicitud) {
        if (solicitud == null) {
            throw new BadRequestException("El cuerpo de la solicitud es obligatorio.");
        }
        if (solicitud.getCantidad() == null || solicitud.getCantidad().signum() <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor que cero.");
        }
    }
}
