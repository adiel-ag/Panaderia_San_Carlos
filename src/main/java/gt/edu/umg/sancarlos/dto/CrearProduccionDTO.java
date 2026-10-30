package gt.edu.umg.sancarlos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Datos de entrada para crear una producción mediante PKG_PRODUCCION.SP_CREAR.
 * El usuario responsable es el de la sesión; un idUsuario en el JSON se ignora.
 */
@JsonIgnoreProperties({"idUsuario"})
public class CrearProduccionDTO {

    private String observacion;

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
