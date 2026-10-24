package gt.edu.umg.sancarlos.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import gt.edu.umg.sancarlos.model.Producto;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProductoJsonSerializationTest {

    @Test
    void serializaLocalDateTimeDelProductoComoJson() throws Exception {
        Class<?> providerClass;
        try {
            providerClass = Class.forName(
                    "gt.edu.umg.sancarlos.resource.JacksonObjectMapperProvider");
        } catch (ClassNotFoundException missing) {
            fail("Debe existir configuración Jackson para los tipos de fecha Java", missing);
            return;
        }
        Object provider = providerClass.getConstructor().newInstance();
        Method getContext = providerClass.getMethod("getContext", Class.class);
        ObjectMapper mapper = (ObjectMapper) getContext.invoke(provider, Producto.class);
        Producto producto = new Producto();
        producto.setFechaCreacion(LocalDateTime.of(2026, 10, 24, 13, 30));

        String json = mapper.writeValueAsString(producto);

        assertNotNull(json);
        assertTrue(json.contains("2026-10-24T13:30"), json);
    }
}
