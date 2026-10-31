package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos para guardar una nueva version de la receta de un producto.
 * versionBase es la ultima version que vio quien edita (null si no habia
 * ninguna): si otro guardo despues, el guardado se rechaza en vez de
 * reemplazar su version sin aviso.
 */
public record NuevaRecetaDTO(Long idProducto, BigDecimal rendimiento,
        List<NuevoIngredienteDTO> ingredientes, Integer versionBase) {

    /** Para validaciones que no dependen de la version base. */
    public NuevaRecetaDTO(Long idProducto, BigDecimal rendimiento,
            List<NuevoIngredienteDTO> ingredientes) {
        this(idProducto, rendimiento, ingredientes, null);
    }
}
