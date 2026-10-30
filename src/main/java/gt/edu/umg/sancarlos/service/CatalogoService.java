package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.CatalogoDAO;
import gt.edu.umg.sancarlos.dto.RolDTO;
import gt.edu.umg.sancarlos.dto.SucursalDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Consultas de catalogos activos. */
public class CatalogoService {
    private final CatalogoDAO catalogoDAO;

    public CatalogoService() {
        this(new CatalogoDAO());
    }

    CatalogoService(CatalogoDAO catalogoDAO) {
        this.catalogoDAO = catalogoDAO;
    }

    public List<RolDTO> roles() {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return catalogoDAO.roles(connection);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar los roles", exception);
        }
    }

    public List<SucursalDTO> sucursales() {
        try (Connection connection = ConnectionFactory.getConnection()) {
            return catalogoDAO.sucursales(connection);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar las sucursales", exception);
        }
    }
}
