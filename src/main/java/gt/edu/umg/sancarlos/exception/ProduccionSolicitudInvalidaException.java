package gt.edu.umg.sancarlos.exception;

/** Indica que los datos enviados para la producción no cumplen sus reglas. */
public class ProduccionSolicitudInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProduccionSolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
