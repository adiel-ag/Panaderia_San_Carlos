package gt.edu.umg.sancarlos.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoQueryTimeoutTest {

    @Test
    void cadaPreparacionEnLosDaosAplicaTimeout() throws Exception {
        Path carpeta = Path.of("src/main/java/gt/edu/umg/sancarlos/dao");
        List<String> daos = List.of("DespachoDAO", "InventarioDAO", "PedidoDAO",
                "ProduccionDAO", "ProductoDAO", "RecetaDAO", "SeguridadDAO",
                "UsuarioDAO", "CatalogoDAO", "ReporteDAO");
        int total = 0;
        for (String dao : daos) {
            String fuente = Files.readString(carpeta.resolve(dao + ".java"));
            int preparaciones = contar(fuente, ".prepareStatement(")
                    + contar(fuente, ".prepareCall(");
            int timeouts = contar(fuente, "ConnectionFactory.aplicarTimeout(statement)");
            assertTrue(preparaciones > 0, dao);
            assertEquals(preparaciones, timeouts, dao);
            total += preparaciones;
        }
        assertTrue(total >= 32, "preparaciones encontradas: " + total);
    }

    private static int contar(String fuente, String texto) {
        return (fuente.length() - fuente.replace(texto, "").length()) / texto.length();
    }
}
