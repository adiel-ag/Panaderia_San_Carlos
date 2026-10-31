package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.service.ReporteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Reportes por rango de fechas (yyyy-MM-dd, inclusivo) para administración y gerencia. */
@Path("/reportes")
@Produces(MediaType.APPLICATION_JSON)
public class ReporteResource {

    private final ReporteService reporteService;

    public ReporteResource() {
        this(new ReporteService());
    }

    ReporteResource(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @GET
    @Path("pedidos-por-sucursal")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE})
    public Response pedidosPorSucursal(@QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        ReporteService.validarRango(inicio, fin);
        return Response.ok(reporteService.pedidosPorSucursal(inicio, fin)).build();
    }

    @GET
    @Path("produccion-por-dia")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE})
    public Response produccionPorDia(@QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        ReporteService.validarRango(inicio, fin);
        return Response.ok(reporteService.produccionPorDia(inicio, fin)).build();
    }

    @GET
    @Path("consumo-insumos")
    @RolesAllowed({Roles.ADMINISTRADOR, Roles.GERENTE})
    public Response consumoInsumos(@QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        ReporteService.validarRango(inicio, fin);
        return Response.ok(reporteService.consumoInsumos(inicio, fin)).build();
    }

    static LocalDate fecha(String texto, String nombre) {
        if (texto == null || texto.isBlank()) {
            throw new BadRequestException("La fecha " + nombre + " es obligatoria.");
        }
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(nombre + " debe tener el formato yyyy-MM-dd.");
        }
    }
}
