package gt.edu.umg.sancarlos.exception;

/** Una regla impide modificar al usuario. */
public class UsuarioConflictoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public UsuarioConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
