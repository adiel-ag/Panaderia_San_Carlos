package gt.edu.umg.sancarlos.exception;

public class CatalogoConflictoException extends RuntimeException {
    public CatalogoConflictoException(String mensaje) { super(mensaje); }
    public CatalogoConflictoException(String mensaje, Throwable causa) { super(mensaje, causa); }
}
