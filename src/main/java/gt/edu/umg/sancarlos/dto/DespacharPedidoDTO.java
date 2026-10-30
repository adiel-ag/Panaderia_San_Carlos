package gt.edu.umg.sancarlos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Datos opcionales para despachar un pedido; el ID del pedido viaja en la ruta
 * y el usuario es el de la sesión (un idUsuario en el JSON se ignora).
 */
@JsonIgnoreProperties({"idUsuario"})
public class DespacharPedidoDTO {

    private String observacion;

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
