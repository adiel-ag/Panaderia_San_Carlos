package gt.edu.umg.sancarlos.exception;

/** Indica que el estado de la producción o una regla de Oracle impide la operación. */
public class ProduccionConflictoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProduccionConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
