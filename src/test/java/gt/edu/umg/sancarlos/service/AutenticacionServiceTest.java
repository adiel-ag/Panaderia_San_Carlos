package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.SeguridadDAO;
import gt.edu.umg.sancarlos.dao.SeguridadDAO.ParametrosClave;
import gt.edu.umg.sancarlos.dao.SeguridadDAO.PerfilAcceso;
import gt.edu.umg.sancarlos.dao.SeguridadDAO.ResultadoAcceso;
import gt.edu.umg.sancarlos.exception.CredencialesInvalidasException;
import gt.edu.umg.sancarlos.exception.CuentaBloqueadaException;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.UsuarioNoEncontradoException;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import gt.edu.umg.sancarlos.service.AutenticacionService.SesionIniciada;
import gt.edu.umg.sancarlos.util.HuellaClave;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flujo de inicio de sesion y asignacion de claves sin Oracle. */
class AutenticacionServiceTest {

    private static final String SALT = Base64.getEncoder().encodeToString(new byte[16]);
    private static final ParametrosClave PARAMETROS =
            new ParametrosClave(HuellaClave.ALGORITMO, 1, SALT);
    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 30, 8, 0);
    private static final UsuarioSesion ADMIN =
            new UsuarioSesion(1, "admin", "Administrador", "ADMINISTRADOR", null);

    /** Registra commit, rollback y cierre de cada conexion abierta. */
    private final List<String> eventos = new ArrayList<>();

    @Test
    void accesoCorrectoDevuelveUsuarioYMomentoDelAccesoYConfirma() {
        DaoFalso dao = new DaoFalso();
        dao.resultados.add(new ResultadoAcceso(1L, 0));

        SesionIniciada sesion = servicio(dao).iniciarSesion("admin", "secreta1".toCharArray());

        assertSame(ADMIN, sesion.usuario());
        assertEquals(INICIO, sesion.inicio());
        assertEquals(HuellaClave.calcular("secreta1".toCharArray(), SALT, 1), dao.huellaRecibida);
        assertTrue(eventos.contains("commit"));
        assertFalse(eventos.contains("rollback"));
    }

    @Test
    void claveIncorrectaConfirmaElIntentoYRespondeCredencialesInvalidas() {
        DaoFalso dao = new DaoFalso();
        dao.resultados.add(new ResultadoAcceso(null, 1));

        CredencialesInvalidasException exception = assertThrows(CredencialesInvalidasException.class,
                () -> servicio(dao).iniciarSesion("admin", "mala".toCharArray()));

        assertEquals("Usuario o contraseña incorrectos.", exception.getMessage());
        assertTrue(eventos.contains("commit"), "el intento fallido debe quedar contado");
    }

    @Test
    void quintoFalloConfirmaYRespondeCuentaBloqueada() {
        DaoFalso dao = new DaoFalso();
        dao.resultados.add(new ResultadoAcceso(null, 2));

        assertThrows(CuentaBloqueadaException.class,
                () -> servicio(dao).iniciarSesion("admin", "mala".toCharArray()));
        assertTrue(eventos.contains("commit"));
    }

    @Test
    void usuarioInexistenteRespondeIgualQueClaveIncorrectaSinValidar() {
        DaoFalso dao = new DaoFalso();
        dao.errorParametros = error(20602);

        CredencialesInvalidasException exception = assertThrows(CredencialesInvalidasException.class,
                () -> servicio(dao).iniciarSesion("nadie", "clave".toCharArray()));

        assertEquals("Usuario o contraseña incorrectos.", exception.getMessage());
        assertEquals(0, dao.llamadasValidar);
    }

    @Test
    void cuentaYaBloqueadaDeshaceYRespondeCuentaBloqueada() {
        DaoFalso dao = new DaoFalso();
        dao.erroresValidar.add(error(20603));

        assertThrows(CuentaBloqueadaException.class,
                () -> servicio(dao).iniciarSesion("admin", "secreta1".toCharArray()));
        assertTrue(eventos.contains("rollback"));
    }

    @Test
    void claveCambiadaDuranteElAccesoSeReintentaUnaVez() {
        DaoFalso dao = new DaoFalso();
        dao.erroresValidar.add(error(20605));
        dao.resultados.add(new ResultadoAcceso(1L, 0));

        SesionIniciada sesion = servicio(dao).iniciarSesion("admin", "secreta1".toCharArray());

        assertSame(ADMIN, sesion.usuario());
        assertEquals(2, dao.llamadasParametros);
    }

    @Test
    void claveCambiadaDosVecesRespondeCredencialesInvalidas() {
        DaoFalso dao = new DaoFalso();
        dao.erroresValidar.add(error(20605));
        dao.erroresValidar.add(error(20605));

        assertThrows(CredencialesInvalidasException.class,
                () -> servicio(dao).iniciarSesion("admin", "secreta1".toCharArray()));
        assertEquals(2, dao.llamadasParametros);
    }

    @Test
    void errorDesconocidoDeshaceYNoExponeElDetalle() {
        DaoFalso dao = new DaoFalso();
        dao.erroresValidar.add(error(942));

        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> servicio(dao).iniciarSesion("admin", "secreta1".toCharArray()));

        assertFalse(exception.getMessage().contains("ORA-"));
        assertTrue(eventos.contains("rollback"));
    }

    @Test
    void validaUsuarioYClaveAntesDeIrAOracle() {
        DaoFalso dao = new DaoFalso();
        AutenticacionService servicio = servicio(dao);

        assertThrows(IllegalArgumentException.class, () -> servicio.iniciarSesion(" ", "x".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> servicio.iniciarSesion("admin", null));
        assertThrows(IllegalArgumentException.class, () -> servicio.iniciarSesion("admin", new char[0]));
        assertTrue(eventos.isEmpty());
    }

    @Test
    void establecerClaveGuardaHuellaNuevaConSaltAleatorio() {
        DaoFalso dao = new DaoFalso();

        servicio(dao).establecerClave(1, "nueva-clave".toCharArray());

        assertEquals(1L, dao.idEstablecido);
        assertEquals(HuellaClave.ALGORITMO, dao.algoritmoEstablecido);
        assertEquals(7, dao.iteracionesEstablecidas);
        assertEquals(16, Base64.getDecoder().decode(dao.saltEstablecido).length);
        assertEquals(HuellaClave.calcular("nueva-clave".toCharArray(), dao.saltEstablecido, 7),
                dao.huellaEstablecida);
        assertTrue(eventos.contains("commit"));
    }

    @Test
    void establecerClaveRechazaClaveCortaYUsuarioInexistente() {
        DaoFalso dao = new DaoFalso();
        assertThrows(IllegalArgumentException.class,
                () -> servicio(dao).establecerClave(1, "corta".toCharArray()));
        assertTrue(eventos.isEmpty());

        dao.errorEstablecer = error(20601);
        assertThrows(UsuarioNoEncontradoException.class,
                () -> servicio(dao).establecerClave(99, "nueva-clave".toCharArray()));
        assertTrue(eventos.contains("rollback"));

        assertThrows(UsuarioNoEncontradoException.class,
                () -> servicio(dao).establecerClave("nadie", "nueva-clave".toCharArray()));
    }

    @Test
    void sesionVigenteSoloSiPerfilActivoYClaveSinCambiosDespuesDeEntrar() {
        DaoFalso dao = new DaoFalso();
        AutenticacionService servicio = servicio(dao);

        dao.perfil = Optional.empty();
        assertTrue(servicio.obtenerSesionVigente(1, INICIO).isEmpty(), "usuario o rol inactivo");

        dao.perfil = Optional.of(new PerfilAcceso(ADMIN, INICIO.minusDays(1), INICIO));
        assertEquals(Optional.of(ADMIN), servicio.obtenerSesionVigente(1, INICIO));
        assertTrue(servicio.obtenerSesionVigente(1, null).isEmpty(), "sesion sin momento de acceso");

        dao.perfil = Optional.of(new PerfilAcceso(ADMIN, INICIO.plusSeconds(1), INICIO));
        assertTrue(servicio.obtenerSesionVigente(1, INICIO).isEmpty(), "clave cambiada despues");
    }

    private AutenticacionService servicio(SeguridadDAO dao) {
        return new AutenticacionService(dao, this::conexion, 7);
    }

    private Connection conexion() {
        eventos.add("abrir");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "commit", "rollback", "close" -> eventos.add(method.getName());
                        default -> { }
                    }
                    return null;
                });
    }

    private static SQLException error(int codigo) {
        return new SQLException("ORA-" + codigo + ": detalle interno", "72000", codigo);
    }

    /** DAO sin Oracle: devuelve lo programado en cada prueba. */
    private static final class DaoFalso extends SeguridadDAO {

        SQLException errorParametros;
        SQLException errorEstablecer;
        final Deque<SQLException> erroresValidar = new ArrayDeque<>();
        final Deque<ResultadoAcceso> resultados = new ArrayDeque<>();
        Optional<PerfilAcceso> perfil = Optional.of(new PerfilAcceso(ADMIN, INICIO.minusDays(1), INICIO));
        int llamadasParametros;
        int llamadasValidar;
        String huellaRecibida;
        Long idEstablecido;
        String algoritmoEstablecido;
        int iteracionesEstablecidas;
        String saltEstablecido;
        String huellaEstablecida;

        @Override
        public ParametrosClave obtenerParametros(Connection connection, String username)
                throws SQLException {
            llamadasParametros++;
            if (errorParametros != null) {
                throw errorParametros;
            }
            return PARAMETROS;
        }

        @Override
        public ResultadoAcceso validarAcceso(Connection connection, String username,
                ParametrosClave parametros, String huella) throws SQLException {
            llamadasValidar++;
            huellaRecibida = huella;
            if (!erroresValidar.isEmpty()) {
                throw erroresValidar.poll();
            }
            return resultados.poll();
        }

        @Override
        public Optional<PerfilAcceso> buscarPerfil(Connection connection, long idUsuario) {
            return perfil;
        }

        @Override
        public void establecerClave(Connection connection, long idUsuario, String algoritmo,
                int iteraciones, String salt, String huella) throws SQLException {
            if (errorEstablecer != null) {
                throw errorEstablecer;
            }
            idEstablecido = idUsuario;
            algoritmoEstablecido = algoritmo;
            iteracionesEstablecidas = iteraciones;
            saltEstablecido = salt;
            huellaEstablecida = huella;
        }

        @Override
        public Optional<Long> buscarIdPorUsername(Connection connection, String username) {
            return Optional.empty();
        }
    }
}
