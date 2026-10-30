package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.config.ConnectionFactory;
import gt.edu.umg.sancarlos.dao.SeguridadDAO;
import gt.edu.umg.sancarlos.dao.SeguridadDAO.ParametrosClave;
import gt.edu.umg.sancarlos.dao.SeguridadDAO.PerfilAcceso;
import gt.edu.umg.sancarlos.dao.SeguridadDAO.ResultadoAcceso;
import gt.edu.umg.sancarlos.exception.CredencialesInvalidasException;
import gt.edu.umg.sancarlos.exception.CuentaBloqueadaException;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.UsuarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.UsuarioSolicitudInvalidaException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.util.HuellaClave;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * Inicio de sesion y asignacion de claves. Java solo calcula huellas; las
 * reglas de acceso y de bloqueo las decide PKG_SEGURIDAD.
 */
public class AutenticacionService {

    static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";
    static final String CUENTA_BLOQUEADA =
            "La cuenta está bloqueada temporalmente por intentos fallidos. "
            + "Intente de nuevo en 15 minutos.";

    // Valores de p_resultado de PKG_SEGURIDAD.SP_VALIDAR_ACCESO.
    static final int ACCESO_CORRECTO = 0;
    static final int CUENTA_BLOQUEADA_AHORA = 2;

    /** Salt de 16 bytes en cero: solo para gastar el mismo tiempo con usuarios inexistentes. */
    private static final String SALT_FICTICIO = "AAAAAAAAAAAAAAAAAAAAAA==";

    private final SeguridadDAO seguridadDAO;
    private final ProveedorConexion conexiones;
    private final int iteracionesNuevas;

    public AutenticacionService() {
        this(new SeguridadDAO(), ConnectionFactory::getConnection, HuellaClave.ITERACIONES);
    }

    AutenticacionService(SeguridadDAO seguridadDAO, ProveedorConexion conexiones,
            int iteracionesNuevas) {
        this.seguridadDAO = seguridadDAO;
        this.conexiones = conexiones;
        this.iteracionesNuevas = iteracionesNuevas;
    }

    @FunctionalInterface
    interface ProveedorConexion {
        Connection abrir() throws SQLException;
    }

    /**
     * Usuario autenticado y el momento del acceso segun Oracle; ese momento
     * permite invalidar la sesion si la clave cambia despues.
     */
    public record SesionIniciada(UsuarioSesion usuario, LocalDateTime inicio) {
    }

    public SesionIniciada iniciarSesion(String username, char[] clave) {
        if (username == null || username.isBlank() || clave == null || clave.length == 0) {
            throw new IllegalArgumentException("El usuario y la contraseña son obligatorios");
        }
        try {
            return intentarAcceso(username, clave);
        } catch (ClaveCambiadaDuranteAcceso primera) {
            // La clave cambio entre leer los parametros y validar: un reintento
            // calcula la huella con los parametros nuevos.
            try {
                return intentarAcceso(username, clave);
            } catch (ClaveCambiadaDuranteAcceso segunda) {
                throw new CredencialesInvalidasException(CREDENCIALES_INVALIDAS, segunda.getCause());
            }
        }
    }

    private SesionIniciada intentarAcceso(String username, char[] clave) {
        ParametrosClave parametros;
        try (Connection connection = conexiones.abrir()) {
            parametros = seguridadDAO.obtenerParametros(connection, username);
        } catch (SQLException exception) {
            if (codigoOracle(exception) == 20602) {
                // Mismo costo que un usuario real: el tiempo de respuesta no
                // debe revelar si el usuario existe.
                HuellaClave.calcular(clave, SALT_FICTICIO, HuellaClave.ITERACIONES);
                throw new CredencialesInvalidasException(CREDENCIALES_INVALIDAS, exception);
            }
            throw new DataAccessException("No fue posible iniciar sesión", exception);
        }

        String huella = HuellaClave.calcular(clave, parametros.salt(), parametros.iteraciones());

        ResultadoAcceso resultado;
        PerfilAcceso perfil = null;
        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                resultado = seguridadDAO.validarAcceso(connection, username, parametros, huella);
                if (resultado.resultado() == ACCESO_CORRECTO) {
                    perfil = seguridadDAO.buscarPerfil(connection, resultado.idUsuario())
                            .orElseThrow(() -> new SQLException(
                                    "No se encontró el perfil tras un acceso correcto"));
                }
                // Tambien en un fallo: el intento debe quedar contado.
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) {
                    throw traducirErrorAcceso(sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible iniciar sesión", exception);
        }

        if (perfil != null) {
            return new SesionIniciada(perfil.usuario(), perfil.ultimoAcceso());
        }
        if (resultado.resultado() == CUENTA_BLOQUEADA_AHORA) {
            throw new CuentaBloqueadaException(CUENTA_BLOQUEADA, null);
        }
        throw new CredencialesInvalidasException(CREDENCIALES_INVALIDAS, null);
    }

    /** Asigna o reemplaza la clave; las sesiones abiertas del usuario dejan de valer. */
    public void establecerClave(long idUsuario, char[] clave) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("El ID de usuario debe ser positivo");
        }
        HuellaClave.Nueva nueva = HuellaClave.calcularNueva(clave, iteracionesNuevas);

        try (Connection connection = conexiones.abrir()) {
            connection.setAutoCommit(false);
            try {
                seguridadDAO.establecerClave(connection, idUsuario, HuellaClave.ALGORITMO,
                        iteracionesNuevas, nueva.salt(), nueva.huella());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                if (exception instanceof SQLException sqlException) {
                    if (codigoOracle(sqlException) == 20601) {
                        throw new UsuarioNoEncontradoException(
                                "No existe un usuario con ID " + idUsuario, sqlException);
                    }
                    throw new DataAccessException("No fue posible asignar la contraseña",
                            sqlException);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible asignar la contraseña", exception);
        }
    }

    /** Comprueba la clave actual, guarda la nueva y devuelve el inicio vigente. */
    public SesionIniciada cambiarClave(UsuarioSesion usuario, char[] claveActual,
            char[] claveNueva) {
        try {
            HuellaClave.validarNueva(claveNueva);
            if (claveActual == null || Arrays.equals(claveActual, claveNueva)) {
                throw new IllegalArgumentException(
                        "La nueva contraseña debe ser distinta de la actual");
            }
            SesionIniciada acceso;
            try {
                acceso = iniciarSesion(usuario.getUsername(), claveActual);
            } catch (CredencialesInvalidasException exception) {
                throw new UsuarioSolicitudInvalidaException(
                        "La contraseña actual no es correcta", exception);
            }
            if (acceso.usuario().getIdUsuario() != usuario.getIdUsuario()) {
                throw new UsuarioSolicitudInvalidaException(
                        "La contraseña actual no es correcta", null);
            }
            establecerClave(usuario.getIdUsuario(), claveNueva);
            try (Connection connection = conexiones.abrir()) {
                PerfilAcceso perfil = seguridadDAO.buscarPerfil(connection, usuario.getIdUsuario())
                        .orElseThrow(() -> new UsuarioNoEncontradoException(
                                "El usuario no existe", null));
                if (perfil.fechaCambioClave() == null) {
                    throw new DataAccessException("No fue posible renovar la sesión", null);
                }
                return new SesionIniciada(perfil.usuario(), perfil.fechaCambioClave());
            } catch (SQLException exception) {
                throw new DataAccessException("No fue posible renovar la sesión", exception);
            }
        } finally {
            if (claveActual != null) Arrays.fill(claveActual, '\0');
            if (claveNueva != null) Arrays.fill(claveNueva, '\0');
        }
    }

    /** Variante por nombre de usuario, para la utilidad de consola. */
    public void establecerClave(String username, char[] clave) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El usuario es obligatorio");
        }
        HuellaClave.validarNueva(clave);
        long idUsuario;
        try (Connection connection = conexiones.abrir()) {
            idUsuario = seguridadDAO.buscarIdPorUsername(connection, username)
                    .orElseThrow(() -> new UsuarioNoEncontradoException(
                            "No existe el usuario " + username, null));
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible buscar el usuario", exception);
        }
        establecerClave(idUsuario, clave);
    }

    /**
     * Revalida una sesion abierta: vacio si el usuario o su rol se
     * desactivaron, si perdio la clave o si la clave cambio despues de entrar.
     */
    public Optional<UsuarioSesion> obtenerSesionVigente(long idUsuario, LocalDateTime inicio) {
        if (inicio == null) {
            return Optional.empty();
        }
        try (Connection connection = conexiones.abrir()) {
            return seguridadDAO.buscarPerfil(connection, idUsuario)
                    .filter(perfil -> perfil.fechaCambioClave() == null
                            || !perfil.fechaCambioClave().isAfter(inicio))
                    .map(PerfilAcceso::usuario);
        } catch (SQLException exception) {
            throw new DataAccessException("No fue posible validar la sesión", exception);
        }
    }

    static RuntimeException traducirErrorAcceso(SQLException exception) {
        return switch (codigoOracle(exception)) {
            case 20602 -> new CredencialesInvalidasException(CREDENCIALES_INVALIDAS, exception);
            case 20603 -> new CuentaBloqueadaException(CUENTA_BLOQUEADA, exception);
            case 20605 -> new ClaveCambiadaDuranteAcceso(exception);
            default -> new DataAccessException("No fue posible iniciar sesión", exception);
        };
    }

    private static int codigoOracle(SQLException exception) {
        return Math.abs(exception.getErrorCode());
    }

    private static void rollback(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    /** -20605: la clave cambio durante el acceso; se reintenta una vez. */
    static final class ClaveCambiadaDuranteAcceso extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ClaveCambiadaDuranteAcceso(SQLException cause) {
            super("La credencial cambió durante el acceso", cause);
        }
    }
}
