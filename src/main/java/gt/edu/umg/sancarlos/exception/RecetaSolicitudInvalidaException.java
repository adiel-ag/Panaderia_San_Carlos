package gt.edu.umg.sancarlos.exception;

/** Datos invalidos en una solicitud de receta. */
public class RecetaSolicitudInvalidaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RecetaSolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
