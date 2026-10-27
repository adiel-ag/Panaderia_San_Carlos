package gt.edu.umg.sancarlos.exception;

/** Indica que no existe la producción o un registro relacionado (usuario, pedido o producto). */
public class ProduccionNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProduccionNoEncontradaException(String message, Throwable cause) {
        super(message, cause);
    }
}
