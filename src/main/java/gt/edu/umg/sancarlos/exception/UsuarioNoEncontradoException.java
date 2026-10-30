package gt.edu.umg.sancarlos.exception;

/** No existe el usuario indicado. */
public class UsuarioNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UsuarioNoEncontradoException(String message, Throwable cause) {
        super(message, cause);
    }
}
