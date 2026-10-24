package gt.edu.umg.sancarlos.exception;

/** Indica que una regla de negocio impide completar la operación del pedido. */
public class PedidoConflictoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
