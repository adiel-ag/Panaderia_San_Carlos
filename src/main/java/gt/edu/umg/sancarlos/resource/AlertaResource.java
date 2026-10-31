package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.service.AlertaService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/** Conteos de alertas para Inicio; el servicio filtra según el rol y la sucursal de sesión. */
@Path("/alertas")
@Produces(MediaType.APPLICATION_JSON)
public class AlertaResource {

    private final AlertaService alertaService;

    public AlertaResource() {
        this(new AlertaService());
    }

    AlertaResource(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GET
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE, Roles.SUCURSAL, Roles.PRODUCCION,
            Roles.BODEGA, Roles.DESPACHO})
    public Response consultar(@Context SecurityContext seguridad) {
        return Response.ok(alertaService.alertas(UsuarioActual.de(seguridad))).build();
    }
}
