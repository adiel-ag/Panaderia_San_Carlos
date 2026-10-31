package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.SucursalAdminDAO;
import gt.edu.umg.sancarlos.dto.SucursalAdminDTO;
import gt.edu.umg.sancarlos.dto.SucursalSolicitudDTO;
import gt.edu.umg.sancarlos.exception.CatalogoConflictoException;
import gt.edu.umg.sancarlos.exception.CatalogoNoEncontradoException;
import gt.edu.umg.sancarlos.exception.CatalogoSolicitudInvalidaException;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Cambios de sucursales con la fila bloqueada durante cada transacción. */
public class SucursalAdminService {
    private final SucursalAdminDAO dao;
    private final ProveedorConexion conexiones;

    @FunctionalInterface
    interface ProveedorConexion { Connection abrir() throws SQLException; }

    public SucursalAdminService() { this(new SucursalAdminDAO(), ConnectionFactory::getConnection); }
    SucursalAdminService(SucursalAdminDAO dao, ProveedorConexion conexiones) {
        this.dao = dao;
        this.conexiones = conexiones;
    }

    public List<SucursalAdminDTO> listar() {
        try (Connection connection = conexiones.abrir()) { return dao.listar(connection); }
        catch (SQLException exception) { throw new DataAccessException("No fue posible listar las sucursales", exception); }
    }

    public SucursalAdminDTO crear(SucursalSolicitudDTO solicitud) {
        validar(solicitud);
        return transaccion(connection -> {
            long id = dao.crear(connection, solicitud.nombre().trim(), limpiar(solicitud.direccion()),
                    limpiar(solicitud.telefono()));
            return buscar(connection, id);
        });
    }

    public SucursalAdminDTO actualizar(long id, SucursalSolicitudDTO solicitud) {
        validarId(id);
        validar(solicitud);
        return transaccion(connection -> {
            bloquear(connection, id);
            dao.actualizar(connection, id, solicitud.nombre().trim(), limpiar(solicitud.direccion()),
                    limpiar(solicitud.telefono()));
            return buscar(connection, id);
        });
    }

    public SucursalAdminDTO cambiarEstado(long id, String estado) {
        validarId(id);
        if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) {
            throw new CatalogoSolicitudInvalidaException("El estado debe ser ACTIVO o INACTIVO");
        }
        return transaccion(connection -> {
            bloquear(connection, id);
            SucursalAdminDTO actual = buscar(connection, id);
            if ("INACTIVO".equals(estado)
                    && (actual.usuariosActivos() > 0 || actual.pedidosAbiertos() > 0)) {
                throw new CatalogoConflictoException("No se puede desactivar: tiene "
                        + actual.usuariosActivos() + " usuarios activos y " + actual.pedidosAbiertos()
                        + " pedidos sin despachar");
            }
            dao.cambiarEstado(connection, id, estado);
            return buscar(connection, id);
        });
    }

    static void validar(SucursalSolicitudDTO solicitud) {
        if (solicitud == null || solicitud.nombre() == null || solicitud.nombre().trim().isEmpty()
                || solicitud.nombre().trim().length() > 100) {
            throw new CatalogoSolicitudInvalidaException("El nombre debe tener entre 1 y 100 caracteres");
        }
        if (solicitud.direccion() != null && solicitud.direccion().trim().length() > 200) {
            throw new CatalogoSolicitudInvalidaException("La dirección admite máximo 200 caracteres");
        }
        if (solicitud.telefono() != null && (solicitud.telefono().trim().length() > 20
                || !solicitud.telefono().matches("[0-9 +()\\-]*"))) {
            throw new CatalogoSolicitudInvalidaException("El teléfono admite dígitos, espacios, +, - y paréntesis; máximo 20 caracteres");
        }
    }

    private static String limpiar(String valor) { return valor == null ? null : valor.trim(); }
    private static void validarId(long id) {
        if (id <= 0) throw new CatalogoSolicitudInvalidaException("El ID debe ser positivo");
    }
    private void bloquear(Connection connection, long id) throws SQLException {
        if (!dao.bloquear(connection, id)) throw new CatalogoNoEncontradoException("No existe una sucursal con ID " + id);
    }
    private SucursalAdminDTO buscar(Connection connection, long id) throws SQLException {
        return dao.buscarPorId(connection, id).orElseThrow(SQLException::new);
    }

    private <T> T transaccion(Trabajo<T> trabajo) {
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException exception) {
                try { connection.rollback(); } catch (SQLException error) { exception.addSuppressed(error); }
                if (exception instanceof SQLException sql) {
                    if (Math.abs(sql.getErrorCode()) == 1) throw new CatalogoConflictoException(
                            "Ya existe una sucursal con ese nombre", sql);
                    throw new DataAccessException("No fue posible guardar la sucursal", sql);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible guardar la sucursal", exception);
        }
    }

    @FunctionalInterface
    private interface Trabajo<T> { T ejecutar(Connection connection) throws SQLException; }
}
