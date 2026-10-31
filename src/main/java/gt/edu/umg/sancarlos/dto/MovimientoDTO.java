package gt.edu.umg.sancarlos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoDTO(long idMovimiento, LocalDateTime fecha, String tipo,
        BigDecimal cantidad, String origen, Long idReferencia, String usuario,
        String observacion, Long idPedido) { }
