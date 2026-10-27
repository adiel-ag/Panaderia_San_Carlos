package gt.edu.umg.sancarlos.exception;

/** Indica que no existe el pedido, el usuario o el despacho solicitado. */
public class DespachoNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DespachoNoEncontradoException(String message, Throwable cause) {
        super(message, cause);
    }
}
