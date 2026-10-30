package gt.edu.umg.sancarlos.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Calcula la huella PBKDF2 de una contrasena. La contrasena nunca se guarda:
 * solo su huella y el salt, ambos en Base64.
 */
public final class HuellaClave {

    public static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    /** Recomendacion de OWASP (2023) para PBKDF2-HMAC-SHA256. */
    public static final int ITERACIONES = 600_000;
    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 128;

    private static final int BYTES_SALT = 16;
    private static final int BITS_HUELLA = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private HuellaClave() {
    }

    public static String generarSalt() {
        byte[] salt = new byte[BYTES_SALT];
        ALEATORIO.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Parametros y huella de una clave nueva; nunca se exponen por REST. */
    public record Nueva(String salt, String huella) {
    }

    public static Nueva calcularNueva(char[] clave, int iteraciones) {
        validarNueva(clave);
        String salt = generarSalt();
        return new Nueva(salt, calcular(clave, salt, iteraciones));
    }

    /** El salt llega en Base64 tal como se guarda en USUARIO_CREDENCIAL. */
    public static String calcular(char[] clave, String salt, int iteraciones) {
        if (clave == null) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        final byte[] bytesSalt;
        try {
            bytesSalt = Base64.getDecoder().decode(salt);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("El salt guardado no es Base64 válido", exception);
        }
        PBEKeySpec especificacion = new PBEKeySpec(clave, bytesSalt, iteraciones, BITS_HUELLA);
        try {
            byte[] huella = SecretKeyFactory.getInstance(ALGORITMO)
                    .generateSecret(especificacion).getEncoded();
            return Base64.getEncoder().encodeToString(huella);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("PBKDF2 no está disponible en esta JVM", exception);
        } finally {
            especificacion.clearPassword();
        }
    }

    /** Regla de longitud para claves nuevas; las existentes no se revalidan. */
    public static void validarNueva(char[] clave) {
        if (clave == null || clave.length < LONGITUD_MINIMA || clave.length > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException("La contraseña debe tener entre "
                    + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA + " caracteres");
        }
    }
}
