package gt.edu.umg.sancarlos.config;

import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebXmlSesionTest {

    @Test
    void cookieDeSesionEsHttpOnlySameSiteStrictYSoloSeRastreaPorCookie() throws Exception {
        DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
        fabrica.setNamespaceAware(true);
        Document documento = fabrica.newDocumentBuilder()
                .parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile());
        Element configuracion = unico(documento.getDocumentElement(), "session-config");
        Element cookie = unico(configuracion, "cookie-config");

        assertEquals("true", textoUnico(cookie, "http-only"));
        int atributosSameSite = 0;
        NodeList atributos = cookie.getElementsByTagNameNS("*", "attribute");
        for (int i = 0; i < atributos.getLength(); i++) {
            Element atributo = (Element) atributos.item(i);
            if ("SameSite".equals(textoUnico(atributo, "attribute-name"))) {
                atributosSameSite++;
                assertEquals("Strict", textoUnico(atributo, "attribute-value"));
            }
        }
        assertEquals(1, atributosSameSite, "La cookie de sesion debe tener SameSite=Strict");
        assertEquals("COOKIE", textoUnico(configuracion, "tracking-mode"));
        assertTrue(Integer.parseInt(textoUnico(configuracion, "session-timeout")) > 0);
    }

    private static Element unico(Element padre, String nombre) {
        NodeList elementos = padre.getElementsByTagNameNS("*", nombre);
        assertEquals(1, elementos.getLength(), "Se esperaba un solo " + nombre);
        return (Element) elementos.item(0);
    }

    private static String textoUnico(Element padre, String nombre) {
        return unico(padre, nombre).getTextContent().trim();
    }
}
