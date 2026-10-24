package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/** Configura Jackson para convertir tipos java.time usados por los modelos REST. */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JacksonObjectMapperProvider implements ContextResolver<ObjectMapper> {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }
}

