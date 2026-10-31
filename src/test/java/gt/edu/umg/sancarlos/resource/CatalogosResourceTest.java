package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.dto.ProductoSolicitudDTO;
import gt.edu.umg.sancarlos.dto.SucursalSolicitudDTO;
import gt.edu.umg.sancarlos.dto.SucursalAdminDTO;
import gt.edu.umg.sancarlos.dto.EstadoCatalogoDTO;
import gt.edu.umg.sancarlos.exception.CatalogoConflictoException;
import gt.edu.umg.sancarlos.exception.CatalogoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.CatalogoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.Producto;
import gt.edu.umg.sancarlos.service.CatalogoService;
import gt.edu.umg.sancarlos.service.ProductoService;
import gt.edu.umg.sancarlos.service.SucursalAdminService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Contratos REST de los catálogos sin base de datos. */
class CatalogosResourceTest {
    private static final ProductoSolicitudDTO PRODUCTO = new ProductoSolicitudDTO("Pan", 1L, 1L, BigDecimal.ZERO);
    private static final SucursalSolicitudDTO SUCURSAL = new SucursalSolicitudDTO("Centro", "", "");

    @Test void productosResponden201Y200() {
        ProductoResource recurso = new ProductoResource(new ProductoService() {
            @Override public Producto crear(ProductoSolicitudDTO s) { return producto(); }
            @Override public Producto actualizar(long id, ProductoSolicitudDTO s) { return producto(); }
            @Override public Producto cambiarEstado(long id, String estado) { return producto(); }
        });
        try (Response creada = recurso.crearProducto(PRODUCTO);
                Response editada = recurso.actualizarProducto("5", PRODUCTO);
                Response estado = recurso.cambiarEstado("5", new EstadoCatalogoDTO("INACTIVO"))) {
            assertEquals(201, creada.getStatus());
            assertEquals("/productos/5", creada.getLocation().toString());
            assertEquals(200, editada.getStatus());
            assertEquals(200, estado.getStatus());
        }
        assertThrows(BadRequestException.class, () -> recurso.actualizarProducto("abc", PRODUCTO));
    }

    @Test void sucursalesResponden201Y200() {
        SucursalAdminService servicio = new SucursalAdminService() {
            private SucursalAdminDTO dato() { return new SucursalAdminDTO(7, "Centro", "", "", "ACTIVO", 0, 0); }
            @Override public List<SucursalAdminDTO> listar() { return List.of(dato()); }
            @Override public SucursalAdminDTO crear(SucursalSolicitudDTO s) { return dato(); }
            @Override public SucursalAdminDTO actualizar(long id, SucursalSolicitudDTO s) { return dato(); }
            @Override public SucursalAdminDTO cambiarEstado(long id, String estado) { return dato(); }
        };
        SucursalResource recurso = new SucursalResource(new CatalogoService(), servicio);
        try (Response lista = recurso.listarTodas(); Response creada = recurso.crearSucursal(SUCURSAL);
                Response editada = recurso.actualizarSucursal("7", SUCURSAL);
                Response estado = recurso.cambiarEstado("7", new EstadoCatalogoDTO("INACTIVO"))) {
            assertEquals(200, lista.getStatus());
            assertEquals(201, creada.getStatus());
            assertEquals("/sucursales/7", creada.getLocation().toString());
            assertEquals(200, editada.getStatus());
            assertEquals(200, estado.getStatus());
        }
        assertThrows(BadRequestException.class, () -> recurso.cambiarEstado("0", null));
    }

    @Test void erroresDeCatalogoTienen400404Y409() {
        ApiExceptionMapper mapper = new ApiExceptionMapper();
        assertEquals(400, mapper.toResponse(new CatalogoSolicitudInvalidaException("inválido")).getStatus());
        assertEquals(404, mapper.toResponse(new CatalogoNoEncontradoException("no existe")).getStatus());
        assertEquals(409, mapper.toResponse(new CatalogoConflictoException("duplicado")).getStatus());
    }

    @Test void rolesDeLecturaYEscritura() throws Exception {
        assertEquals(Set.of("ADMINISTRADOR", "BODEGA"), Set.of(ProductoResource.class
                .getMethod("crearProducto", ProductoSolicitudDTO.class).getAnnotation(RolesAllowed.class).value()));
        assertEquals(Set.of("ADMINISTRADOR"), Set.of(SucursalResource.class
                .getMethod("listarTodas").getAnnotation(RolesAllowed.class).value()));
        assertEquals(Set.of("ADMINISTRADOR", "GERENTE", "SUCURSAL", "PRODUCCION", "BODEGA", "DESPACHO"),
                Set.of(CatalogoResource.class.getMethod("unidades").getAnnotation(RolesAllowed.class).value()));
    }

    private static Producto producto() {
        Producto producto = new Producto();
        producto.setIdProducto(5);
        return producto;
    }
}
