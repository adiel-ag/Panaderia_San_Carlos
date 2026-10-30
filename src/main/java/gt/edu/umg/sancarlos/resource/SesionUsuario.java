package gt.edu.umg.sancarlos.resource;

import gt.edu.umg.sancarlos.service.AutenticacionService.SesionIniciada;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;

/**
 * Lo que se guarda en la HttpSession: solo el ID del usuario y el momento del
 * acceso. Rol, sucursal y estado se vuelven a leer en cada peticion para que
 * un cambio en Oracle se aplique de inmediato.
 */
final class SesionUsuario {

    static final String ATRIBUTO_ID_USUARIO = "panaderia.idUsuario";
    static final String ATRIBUTO_INICIO = "panaderia.inicio";

    private SesionUsuario() {
    }

    /** Descarta la sesion anterior y crea una nueva: evita la fijacion de sesion. */
    static void iniciar(HttpServletRequest request, SesionIniciada sesion) {
        cerrar(request);
        HttpSession nueva = request.getSession(true);
        nueva.setAttribute(ATRIBUTO_ID_USUARIO, sesion.usuario().getIdUsuario());
        nueva.setAttribute(ATRIBUTO_INICIO, sesion.inicio());
    }

    static void cerrar(HttpServletRequest request) {
        HttpSession actual = request.getSession(false);
        if (actual != null) {
            try {
                actual.invalidate();
            } catch (IllegalStateException yaInvalidada) {
                // Otra peticion la invalido primero: el resultado es el mismo.
            }
        }
    }

    static Long idUsuario(HttpSession sesion) {
        Object valor = sesion.getAttribute(ATRIBUTO_ID_USUARIO);
        return valor instanceof Long id ? id : null;
    }

    static LocalDateTime inicio(HttpSession sesion) {
        Object valor = sesion.getAttribute(ATRIBUTO_INICIO);
        return valor instanceof LocalDateTime inicio ? inicio : null;
    }
}
