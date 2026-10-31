package gt.edu.umg.sancarlos.dao;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dto.RolDTO;
import gt.edu.umg.sancarlos.dto.SucursalDTO;
import gt.edu.umg.sancarlos.dto.UnidadCatalogoDTO;
import gt.edu.umg.sancarlos.dto.TipoProductoDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Lectura de roles y sucursales activos. */
public class CatalogoDAO {

    public List<UnidadCatalogoDTO> unidades(Connection connection) throws SQLException {
        List<UnidadCatalogoDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_unidad, nombre, abreviatura FROM UNIDAD_MEDIDA ORDER BY nombre")) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(new UnidadCatalogoDTO(rows.getLong(1),
                        rows.getString(2), rows.getString(3)));
            }
        }
        return items;
    }

    public List<TipoProductoDTO> tiposProducto(Connection connection) throws SQLException {
        List<TipoProductoDTO> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_tipo_producto, nombre FROM TIPO_PRODUCTO ORDER BY nombre")) {
            ConnectionFactory.aplicarTimeout(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) items.add(new TipoProductoDTO(rows.getLong(1), rows.getString(2)));
            }
        }
        return items;
    }

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
