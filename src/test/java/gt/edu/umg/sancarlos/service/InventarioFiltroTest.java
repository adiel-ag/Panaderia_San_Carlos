package gt.edu.umg.sancarlos.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventarioFiltroTest {

    @Test
    void escapaLosComodinesDeLikeSinAlterarLasLetras() {
        assertEquals("a\\\\b\\%c\\_d", InventarioService.escaparLike("a\\b%c_d"));
    }
}
