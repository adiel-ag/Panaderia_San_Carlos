package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/** Existencia de un producto según PKG_INVENTARIO.FN_EXISTENCIA. */
public class ExistenciaDTO {

    private final long idProducto;
    private final BigDecimal existencia;

    public ExistenciaDTO(long idProducto, BigDecimal existencia) {
        this.idProducto = idProducto;
        this.existencia = existencia;
    }

    public long getIdProducto() {
        return idProducto;
    }

    public BigDecimal getExistencia() {
        return existencia;
    }
}
