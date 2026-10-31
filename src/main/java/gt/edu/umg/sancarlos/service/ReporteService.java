package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.ReporteDAO;
import gt.edu.umg.sancarlos.dto.ConsumoInsumoDTO;
import gt.edu.umg.sancarlos.dto.ReportePedidosSucursalDTO;
import gt.edu.umg.sancarlos.dto.ReporteProduccionDiaDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Reportes por rango de fechas inclusivo; solo lectura. */
public class ReporteService {

    /** Máximo de días que abarca un reporte, contando ambos extremos. */
    public static final int MAXIMO_DIAS = 366;

    private final ReporteDAO reporteDAO;

    public ReporteService() {
        this(new ReporteDAO());
    }

    ReporteService(ReporteDAO reporteDAO) {
        this.reporteDAO = reporteDAO;
    }

    public List<ReportePedidosSucursalDTO> pedidosPorSucursal(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return consultar("No fue posible generar el reporte de pedidos",
                connection -> reporteDAO.pedidosPorSucursal(connection, desde, hasta));
    }

    public List<ReporteProduccionDiaDTO> produccionPorDia(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return consultar("No fue posible generar el reporte de producción",
                connection -> reporteDAO.produccionPorDia(connection, desde, hasta));
    }

    public List<ConsumoInsumoDTO> consumoInsumos(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return consultar("No fue posible generar el reporte de consumo",
                connection -> reporteDAO.consumoInsumos(connection, desde, hasta));
    }

    /** Exige ambas fechas, desde <= hasta y un rango de hasta 366 días. */
    public static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas desde y hasta son obligatorias.");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha desde no puede ser posterior a hasta.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw new IllegalArgumentException(
                    "El rango no puede superar " + MAXIMO_DIAS + " días.");
        }
    }

    private static <T> T consultar(String mensajeError, Consulta<T> consulta) {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return consulta.ejecutar(connection);
        } catch (SQLException exception) {
            throw new DataAccessException(mensajeError, exception);
        }
    }

    @FunctionalInterface
    private interface Consulta<T> {
        T ejecutar(Connection connection) throws SQLException;
    }
}
