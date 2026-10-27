package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

/**
 * Cantidad producida de un producto. Reemplaza el valor anterior registrado;
 * PKG_PRODUCCION.SP_REGISTRAR_PRODUCIDO no acumula cantidades.
 */
public class CantidadProducidaDTO {

    private BigDecimal cantidadProducida;

    public BigDecimal getCantidadProducida() { return cantidadProducida; }
    public void setCantidadProducida(BigDecimal cantidadProducida) { this.cantidadProducida = cantidadProducida; }
}
