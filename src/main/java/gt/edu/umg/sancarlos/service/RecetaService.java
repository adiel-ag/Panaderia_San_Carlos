package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.RecetaDAO;
import gt.edu.umg.sancarlos.dto.RecetaActivaDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.RecetaAmbiguaException;
import gt.edu.umg.sancarlos.exception.RecetaNoEncontradaException;
import java.sql.Connection;
import java.sql.SQLException;

/** Coordina la consulta de una receta activa por producto. */
public class RecetaService {

    private final RecetaDAO recetaDAO;

    public RecetaService() {
        this(new RecetaDAO());
    }

    RecetaService(RecetaDAO recetaDAO) {
        this.recetaDAO = recetaDAO;
    }

    public RecetaActivaDTO buscarRecetaActiva(long idProducto) {
        if (idProducto <= 0) {
            throw new IllegalArgumentException("El ID del producto debe ser positivo");
        }

        try (Connection connection = ConnectionFactory.getConnection()) {
            long idReceta = recetaDAO.buscarIdRecetaActiva(connection, idProducto);
            return new RecetaActivaDTO(idProducto, idReceta);
        } catch (SQLException exception) {
            int codigoOracle = Math.abs(exception.getErrorCode());
            if (codigoOracle == 20201) {
                throw new RecetaNoEncontradaException(
                        "El producto no posee una receta activa", exception);
            }
            if (codigoOracle == 20202) {
                throw new RecetaAmbiguaException(
                        "El producto posee más de una receta activa", exception);
            }
            throw new DataAccessException(
                    "No fue posible consultar la receta activa del producto", exception);
        }
    }
}
