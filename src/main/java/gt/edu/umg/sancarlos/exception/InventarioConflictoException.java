package gt.edu.umg.sancarlos.exception;

/** Indica que la existencia actual impide completar el movimiento de inventario. */
public class InventarioConflictoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InventarioConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
