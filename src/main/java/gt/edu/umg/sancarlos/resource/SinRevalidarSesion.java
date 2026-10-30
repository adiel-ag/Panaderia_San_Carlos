package gt.edu.umg.sancarlos.resource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El endpoint no necesita saber quien es el usuario, por lo que SesionFilter
 * no consulta Oracle para revalidar la sesion. Asi el logout funciona aunque
 * la base falle o tarde en responder.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface SinRevalidarSesion {
}
