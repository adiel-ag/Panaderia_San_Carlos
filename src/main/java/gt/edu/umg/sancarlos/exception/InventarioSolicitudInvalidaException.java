package gt.edu.umg.sancarlos.exception;

/** Indica que los datos del movimiento de inventario no cumplen sus reglas. */
public class InventarioSolicitudInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InventarioSolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
