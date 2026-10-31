package gt.edu.umg.sancarlos.exception;

/** Una regla de negocio impide guardar la receta. */
public class RecetaConflictoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public RecetaConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
