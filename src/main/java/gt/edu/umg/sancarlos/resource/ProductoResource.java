package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.service.ProductoService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

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
}
