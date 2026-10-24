package gt.edu.umg.sancarlos.exception;

/**
 * Indica que ocurrió un error al acceder a los datos.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
