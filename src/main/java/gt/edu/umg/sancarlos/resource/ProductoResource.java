package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.dto.ProductoSolicitudDTO;
import gt.edu.umg.sancarlos.dto.EstadoCatalogoDTO;
import gt.edu.umg.sancarlos.service.ProductoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.net.URI;

/** Endpoints REST de consulta del catálogo de productos. */
@Path("/productos")
@Produces(MediaType.APPLICATION_JSON)
public class ProductoResource {

    private final ProductoService productoService;

    public ProductoResource() {
        this(new ProductoService());
    }

    ProductoResource(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response listarProductos() {
        List<Producto> productos = productoService.listarProductos();
        return Response.ok(productos).build();
    }

    @GET
    @Path("{id}")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL,
            Roles.PRODUCCION, Roles.BODEGA, Roles.DESPACHO})
    public Response buscarProductoPorId(@PathParam("id") String id) {
        final long idProducto;
        try {
            idProducto = Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
        }
        if (idProducto <= 0) {
            throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
        }

        Producto producto = productoService.buscarProductoPorId(idProducto)
                .orElseThrow(() -> new NotFoundException(
                        "No existe un producto con ID " + idProducto
                ));
        return Response.ok(producto).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.BODEGA})
    public Response crearProducto(ProductoSolicitudDTO solicitud) {
        Producto creado = productoService.crear(solicitud);
        return Response.created(URI.create("/productos/" + creado.getIdProducto())).entity(creado).build();
    }

    @PUT @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.BODEGA})
    public Response actualizarProducto(@PathParam("id") String id, ProductoSolicitudDTO solicitud) {
        return Response.ok(productoService.actualizar(parsearId(id), solicitud)).build();
    }

    @PUT @Path("{id}/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.BODEGA})
    public Response cambiarEstado(@PathParam("id") String id, EstadoCatalogoDTO solicitud) {
        return Response.ok(productoService.cambiarEstado(parsearId(id),
                solicitud == null ? null : solicitud.estado())).build();
    }

    private static long parsearId(String texto) {
        try {
            long id = Long.parseLong(texto);
            if (id > 0) return id;
        } catch (NumberFormatException exception) {
            // Mismo mensaje para todos los ID inválidos.
        }
        throw new BadRequestException("El ID del producto debe ser un número entero positivo.");
    }
}
