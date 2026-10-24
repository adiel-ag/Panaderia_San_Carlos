package gt.edu.umg.sancarlos.resource;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Registra los recursos JAX-RS bajo el prefijo /api. */
@ApplicationPath("/api")
public class RestApplication extends Application {
}
