package gt.edu.umg.sancarlos.dto;

import java.time.LocalDateTime;

/** Cabecera de produccion para los listados. */
public record ProduccionResumenDTO(long idProduccion, long idUsuario, String nombreUsuario,
        LocalDateTime fechaProduccion, String estado, String observacion, long cantidadPedidos) {
}
