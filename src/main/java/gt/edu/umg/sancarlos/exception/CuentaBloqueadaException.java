package gt.edu.umg.sancarlos.exception;

/** La cuenta está bloqueada temporalmente por intentos fallidos. */
public class CuentaBloqueadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CuentaBloqueadaException(String message, Throwable cause) {
        super(message, cause);
    }
}
