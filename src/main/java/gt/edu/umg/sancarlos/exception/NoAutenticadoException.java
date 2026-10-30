package gt.edu.umg.sancarlos.exception;

/** La operación requiere una sesión iniciada. */
public class NoAutenticadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NoAutenticadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
