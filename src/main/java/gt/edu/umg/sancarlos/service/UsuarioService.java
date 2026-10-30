package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.SeguridadDAO;
import gt.edu.umg.sancarlos.dao.UsuarioDAO;
import gt.edu.umg.sancarlos.dto.ActualizarUsuarioDTO;
import gt.edu.umg.sancarlos.dto.CrearUsuarioDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.UsuarioDTO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.UsuarioConflictoException;
import gt.edu.umg.sancarlos.exception.UsuarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.UsuarioSolicitudInvalidaException;
import gt.edu.umg.sancarlos.util.HuellaClave;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/** Coordina las transacciones de administracion de usuarios. */
public class UsuarioService {

    private final UsuarioDAO usuarioDAO;
    private final SeguridadDAO seguridadDAO;
    private final ProveedorConexion conexiones;
    private final int iteraciones;

    @FunctionalInterface
    interface ProveedorConexion {
        Connection abrir() throws SQLException;
    }

    public UsuarioService() {
        this(new UsuarioDAO(), new SeguridadDAO(), ConnectionFactory::getConnection,
                HuellaClave.ITERACIONES);
    }

    UsuarioService(UsuarioDAO usuarioDAO, SeguridadDAO seguridadDAO,
            ProveedorConexion conexiones, int iteraciones) {
        this.usuarioDAO = usuarioDAO;
        this.seguridadDAO = seguridadDAO;
        this.conexiones = conexiones;
        this.iteraciones = iteraciones;
    }

    public Pagina<UsuarioDTO> listar(int pagina, int tamano, String rol,
            String estado, Long idSucursal) {
        if (pagina < 1 || tamano < 1 || tamano > 100) {
            throw new UsuarioSolicitudInvalidaException("La página o el tamaño no son válidos", null);
        }
        if (rol != null) validarRol(rol);
        if (estado != null) validarEstado(estado);
        validarSucursal(idSucursal);
        try (Connection connection = conexiones.abrir()) {
            return usuarioDAO.listar(connection, pagina, tamano, rol, estado, idSucursal);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible listar los usuarios", exception);
        }
    }

    public UsuarioDTO buscarPorId(long id) {
        validarId(id);
        try (Connection connection = conexiones.abrir()) {
            return usuarioDAO.buscarPorId(connection, id)
                    .orElseThrow(() -> noEncontrado(id, null));
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible consultar el usuario", exception);
        }
    }

    public UsuarioDTO crear(CrearUsuarioDTO solicitud, char[] clave) {
        try {
            if (solicitud == null) {
                throw new UsuarioSolicitudInvalidaException("Los datos del usuario son obligatorios", null);
            }
            HuellaClave.validarNueva(clave);
            validarNombre(solicitud.nombre());
            String username = solicitud.username() == null ? null : solicitud.username().trim();
            if (username == null || !username.matches("[a-z0-9.-]{3,50}")) {
                throw new UsuarioSolicitudInvalidaException("El nombre de acceso no es válido", null);
            }
            validarRol(solicitud.rol());
            validarSucursal(solicitud.idSucursal());
            HuellaClave.Nueva nueva = HuellaClave.calcularNueva(clave, iteraciones);
            return transaccion(connection -> {
                long rol = resolverRol(connection, solicitud.rol());
                long id = usuarioDAO.crear(connection, solicitud.nombre().trim(), username,
                        rol, solicitud.idSucursal());
                seguridadDAO.establecerClave(connection, id, HuellaClave.ALGORITMO,
                        iteraciones, nueva.salt(), nueva.huella());
                return leer(connection, id);
            });
        } finally {
            if (clave != null) Arrays.fill(clave, '\0');
        }
    }

    public UsuarioDTO actualizar(long id, ActualizarUsuarioDTO solicitud) {
        validarId(id);
        if (solicitud == null) {
            throw new UsuarioSolicitudInvalidaException("Los datos del usuario son obligatorios", null);
        }
        validarNombre(solicitud.nombre());
        validarRol(solicitud.rol());
        validarSucursal(solicitud.idSucursal());
        return transaccion(connection -> {
            long rol = resolverRol(connection, solicitud.rol());
            usuarioDAO.actualizar(connection, id, solicitud.nombre().trim(), rol,
                    solicitud.idSucursal());
            return leer(connection, id);
        });
    }

    public UsuarioDTO cambiarEstado(long id, String estado) {
        validarId(id);
        validarEstado(estado);
        return transaccion(connection -> {
            usuarioDAO.cambiarEstado(connection, id, estado);
            return leer(connection, id);
        });
    }

    public UsuarioDTO desbloquear(long id) {
        validarId(id);
        return transaccion(connection -> {
            usuarioDAO.desbloquear(connection, id);
            return leer(connection, id);
        });
    }

    private UsuarioDTO leer(Connection connection, long id) throws SQLException {
        return usuarioDAO.buscarPorId(connection, id)
                .orElseThrow(() -> noEncontrado(id, null));
    }

    private long resolverRol(Connection connection, String rol) throws SQLException {
        return usuarioDAO.buscarIdRolActivo(connection, rol)
                .orElseThrow(() -> new UsuarioSolicitudInvalidaException(
                        "El rol no existe o está inactivo", null));
    }

    private <T> T transaccion(Trabajo<T> trabajo) {
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                T resultado = trabajo.ejecutar(connection);
                connection.commit();
                return resultado;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException errorRollback) {
                    exception.addSuppressed(errorRollback);
                }
                if (exception instanceof SQLException sql) throw traducirErrorOracle(sql);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible administrar el usuario", exception);
        }
    }

    @FunctionalInterface
    private interface Trabajo<T> {
        T ejecutar(Connection connection) throws SQLException;
    }

    static RuntimeException traducirErrorOracle(SQLException exception) {
        return switch (Math.abs(exception.getErrorCode())) {
            case 20701 -> new UsuarioSolicitudInvalidaException("Los datos del usuario no son válidos", exception);
            case 20702 -> new UsuarioConflictoException("El usuario ya existe", exception);
            case 20703 -> new UsuarioSolicitudInvalidaException("El rol no existe o está inactivo", exception);
            case 20704 -> new UsuarioSolicitudInvalidaException("La sucursal no corresponde al rol", exception);
            case 20705 -> new UsuarioNoEncontradoException("El usuario no existe", exception);
            case 20706 -> new UsuarioConflictoException(
                    "No se puede quitar el último administrador activo", exception);
            case 20707 -> new UsuarioConflictoException(
                    "El usuario no tiene contraseña asignada", exception);
            case 60 -> new UsuarioConflictoException(
                    "Otro cambio de administradores está en curso; intente de nuevo", exception);
            case 20601 -> new UsuarioNoEncontradoException("El usuario no existe", exception);
            default -> new DataAccessException("No fue posible administrar el usuario", exception);
        };
    }

    private static UsuarioNoEncontradoException noEncontrado(long id, Throwable cause) {
        return new UsuarioNoEncontradoException("No existe un usuario con ID " + id, cause);
    }

    private static void validarId(long id) {
        if (id <= 0) throw new UsuarioSolicitudInvalidaException("El ID del usuario debe ser positivo", null);
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty() || nombre.trim().length() > 100) {
            throw new UsuarioSolicitudInvalidaException("El nombre debe tener entre 1 y 100 caracteres", null);
        }
    }

    /** Solo el formato: si el rol existe y esta activo lo decide la tabla ROL. */
    private static void validarRol(String rol) {
        if (rol == null || !rol.matches("[A-Z_]{1,50}")) {
            throw new UsuarioSolicitudInvalidaException("El rol no es válido", null);
        }
    }

    private static void validarEstado(String estado) {
        if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) {
            throw new UsuarioSolicitudInvalidaException("El estado no es válido", null);
        }
    }

    private static void validarSucursal(Long idSucursal) {
        if (idSucursal != null && idSucursal <= 0) {
            throw new UsuarioSolicitudInvalidaException("El ID de sucursal debe ser positivo", null);
        }
    }
}
