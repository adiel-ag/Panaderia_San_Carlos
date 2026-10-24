package gt.edu.umg.sancarlos.exception;

/** Indica una inconsistencia: el producto tiene más de una receta activa. */
public class RecetaAmbiguaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecetaAmbiguaException(String message, Throwable cause) {
        super(message, cause);
    }
}
