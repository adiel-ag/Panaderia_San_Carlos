package gt.edu.umg.sancarlos.exception;

/** Indica que no existe el producto o el usuario indicado para el inventario. */
public class InventarioNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InventarioNoEncontradoException(String message, Throwable cause) {
        super(message, cause);
    }
}
