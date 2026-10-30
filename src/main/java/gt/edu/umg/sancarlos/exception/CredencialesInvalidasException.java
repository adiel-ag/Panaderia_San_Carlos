package gt.edu.umg.sancarlos.exception;

/** Usuario o contraseña incorrectos; el mensaje nunca dice cuál de los dos falló. */
public class CredencialesInvalidasException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CredencialesInvalidasException(String message, Throwable cause) {
        super(message, cause);
    }
}
