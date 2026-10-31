package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;

public record ProductoSolicitudDTO(String nombre, Long idTipoProducto, Long idUnidad,
        BigDecimal stockMinimo) { }
