package gt.edu.umg.sancarlos.exception;

/** Indica que el estado del pedido o el inventario impiden registrar el despacho. */
public class DespachoConflictoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DespachoConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
