package gt.edu.umg.sancarlos.exception;

/** Datos invalidos en una solicitud de usuario. */
public class UsuarioSolicitudInvalidaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public UsuarioSolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
