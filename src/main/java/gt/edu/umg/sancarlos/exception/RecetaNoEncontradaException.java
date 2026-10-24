package gt.edu.umg.sancarlos.exception;

/** Indica que el producto no tiene una receta activa. */
public class RecetaNoEncontradaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RecetaNoEncontradaException(String message, Throwable cause) {
        super(message, cause);
    }
}
