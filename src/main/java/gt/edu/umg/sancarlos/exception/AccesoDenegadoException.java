package gt.edu.umg.sancarlos.exception;

/** El rol del usuario no tiene permiso para la operación. */
public class AccesoDenegadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AccesoDenegadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
