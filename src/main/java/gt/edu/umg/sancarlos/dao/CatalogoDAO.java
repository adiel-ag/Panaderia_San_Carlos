package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.RolDTO;
import gt.edu.umg.sancarlos.dto.SucursalDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Lectura de roles y sucursales activos. */
public class CatalogoDAO {

    public List<RolDTO> roles(Connection connection) throws SQLException {
        List<RolDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_rol, nombre, descripcion FROM ROL WHERE estado = 'ACTIVO' ORDER BY nombre")) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(new RolDTO(rows.getLong("id_rol"), rows.getString("nombre"),
                            rows.getString("descripcion")));
                }
            }
        }
        return items;
    }

    public List<SucursalDTO> sucursales(Connection connection) throws SQLException {
        List<SucursalDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_sucursal, nombre FROM SUCURSAL WHERE estado = 'ACTIVO' ORDER BY nombre")) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    items.add(new SucursalDTO(rows.getLong("id_sucursal"), rows.getString("nombre")));
                }
            }
        }
        return items;
    }
}
