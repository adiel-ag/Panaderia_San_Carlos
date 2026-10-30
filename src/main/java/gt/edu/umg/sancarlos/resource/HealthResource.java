package gt.edu.umg.sancarlos.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

/** Endpoint de diagnóstico de disponibilidad de la API; público, sin sesión. */
@Path("/health")
public class HealthResource {

    @GET
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
