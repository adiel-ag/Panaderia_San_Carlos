package gt.edu.umg.sancarlos.dto;

/** Respuesta de creación de un pedido. */
public class PedidoCreadoDTO {

    private final long idPedido;

    public PedidoCreadoDTO(long idPedido) {
        this.idPedido = idPedido;
    }

    public long getIdPedido() {
        return idPedido;
    }
}
