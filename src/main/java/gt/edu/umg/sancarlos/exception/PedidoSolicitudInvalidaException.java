package gt.edu.umg.sancarlos.exception;

/** Indica que los datos enviados para el pedido no cumplen sus reglas. */
public class PedidoSolicitudInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PedidoSolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
