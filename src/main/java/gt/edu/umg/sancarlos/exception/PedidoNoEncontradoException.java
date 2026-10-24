package gt.edu.umg.sancarlos.exception;

/** Indica que no existe el pedido, usuario, sucursal o producto solicitado. */
public class PedidoNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoNoEncontradoException(String message, Throwable cause) {
        super(message, cause);
    }
}
