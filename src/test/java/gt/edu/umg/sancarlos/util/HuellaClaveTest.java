package gt.edu.umg.sancarlos.util;

import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuellaClaveTest {

    @Test
    void coincideConElVectorOficialDelRfc7914() {
        // RFC 7914, seccion 11: PBKDF2-HMAC-SHA256 con P="passwd", S="salt", c=1;
        // los primeros 32 bytes de la salida.
        String esperado = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(
                "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"));
        String salt = Base64.getEncoder().encodeToString("salt".getBytes());

        assertEquals(esperado, HuellaClave.calcular("passwd".toCharArray(), salt, 1));
    }

    @Test
    void esDeterministaYDependeDelSaltYDeLasIteraciones() {
        String salt = HuellaClave.generarSalt();
        String huella = HuellaClave.calcular("clave-segura".toCharArray(), salt, 10);

        assertEquals(huella, HuellaClave.calcular("clave-segura".toCharArray(), salt, 10));
        assertNotEquals(huella, HuellaClave.calcular("clave-segura".toCharArray(),
                HuellaClave.generarSalt(), 10));
        assertNotEquals(huella, HuellaClave.calcular("clave-segura".toCharArray(), salt, 11));
        assertNotEquals(huella, HuellaClave.calcular("otra-clave".toCharArray(), salt, 10));
    }

    @Test
    void generaSaltsAleatoriosDe16BytesYHuellasDe256Bits() {
        String salt = HuellaClave.generarSalt();

        assertEquals(16, Base64.getDecoder().decode(salt).length);
        assertNotEquals(salt, HuellaClave.generarSalt());
        assertEquals(32, Base64.getDecoder().decode(
                HuellaClave.calcular("x".toCharArray(), salt, 1)).length);
    }

    @Test
    void validaLongitudDeClavesNuevas() {
        assertThrows(IllegalArgumentException.class, () -> HuellaClave.validarNueva(null));
        assertThrows(IllegalArgumentException.class,
                () -> HuellaClave.validarNueva("1234567".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> HuellaClave.validarNueva(new char[129]));
        assertDoesNotThrow(() -> HuellaClave.validarNueva("12345678".toCharArray()));
        assertDoesNotThrow(() -> HuellaClave.validarNueva(new char[128]));
    }

    @Test
    void rechazaClaveNulaYSaltCorrupto() {
        assertThrows(IllegalArgumentException.class,
                () -> HuellaClave.calcular(null, HuellaClave.generarSalt(), 1));
        assertThrows(IllegalStateException.class,
                () -> HuellaClave.calcular("x".toCharArray(), "no es base64!", 1));
    }
}
