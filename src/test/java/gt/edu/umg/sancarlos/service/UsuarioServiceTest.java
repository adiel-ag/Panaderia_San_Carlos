package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.SeguridadDAO;
import gt.edu.umg.sancarlos.dao.UsuarioDAO;
import gt.edu.umg.sancarlos.dto.CrearUsuarioDTO;
import gt.edu.umg.sancarlos.dto.Pagina;
import gt.edu.umg.sancarlos.dto.UsuarioDTO;
import gt.edu.umg.sancarlos.exception.UsuarioConflictoException;
import gt.edu.umg.sancarlos.exception.UsuarioNoEncontradoException;
import gt.edu.umg.sancarlos.exception.UsuarioSolicitudInvalidaException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transacciones de usuario sin Oracle. */
class UsuarioServiceTest {
    private final List<String> eventos = new ArrayList<>();
    private final UsuarioDAOFalso usuarios = new UsuarioDAOFalso();
    private final SeguridadDAOFalso seguridad = new SeguridadDAOFalso();

    @Test
    void crearUsuarioYClaveUsaUnaTransaccionYUnCommit() {
        char[] clave = "clave-nueva-1".toCharArray();
        UsuarioDTO creado = servicio().crear(solicitud(), clave);

        assertEquals(7, creado.idUsuario());
        assertEquals(List.of("abrir", "autoCommit:false", "rol", "crear",
                "clave", "leer", "commit", "close"), eventos);
        assertArrayEquals(new char[clave.length], clave);
        assertEquals(7, seguridad.idUsuario);
    }

    @Test
    void fallaLaClaveYRevierteLaCreacion() {
        seguridad.error = new SQLException("detalle secreto", "72000", 20601);

        assertThrows(UsuarioNoEncontradoException.class,
                () -> servicio().crear(solicitud(), "clave-nueva-1".toCharArray()));
        assertEquals(List.of("abrir", "autoCommit:false", "rol", "crear",
                "clave", "rollback", "close"), eventos);
    }

    @Test
    void claveInvalidaYPaginacionInvalidaNoAbrenConexion() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio().crear(solicitud(), "corta".toCharArray()));
        assertThrows(UsuarioSolicitudInvalidaException.class,
                () -> servicio().listar(0, 20, null, null, null));
        assertThrows(UsuarioSolicitudInvalidaException.class,
                () -> servicio().listar(1, 101, null, null, null));
        assertThrows(UsuarioSolicitudInvalidaException.class,
                () -> servicio().listar(1, 20, "rol con espacios", null, null));
        assertThrows(UsuarioSolicitudInvalidaException.class,
                () -> servicio().listar(1, 20, null, "OTRO", null));
        assertThrows(UsuarioSolicitudInvalidaException.class,
                () -> servicio().listar(1, 20, null, null, -1L));
        assertTrue(eventos.isEmpty());
    }

    @Test
    void traduceCadaCodigoDeUsuarioSinTextoOracle() {
        for (int codigo : new int[]{20701, 20703, 20704}) {
            assertInstanceOf(UsuarioSolicitudInvalidaException.class,
                    UsuarioService.traducirErrorOracle(error(codigo)));
        }
        for (int codigo : new int[]{20702, 20706, 20707}) {
            assertInstanceOf(UsuarioConflictoException.class,
                    UsuarioService.traducirErrorOracle(error(codigo)));
        }
        assertInstanceOf(UsuarioNoEncontradoException.class,
                UsuarioService.traducirErrorOracle(error(20705)));
        assertInstanceOf(UsuarioConflictoException.class,
                UsuarioService.traducirErrorOracle(error(60)));
        for (int codigo = 20701; codigo <= 20707; codigo++) {
            assertFalse(UsuarioService.traducirErrorOracle(error(codigo))
                    .getMessage().contains("ORA-"));
        }
    }

    private UsuarioService servicio() {
        return new UsuarioService(usuarios, seguridad, this::conexion, 1);
    }

    private Connection conexion() {
        eventos.add("abrir");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> eventos.add("autoCommit:" + args[0]);
                        case "commit", "rollback", "close" -> eventos.add(method.getName());
                        default -> { }
                    }
                    return null;
                });
    }

    private static SQLException error(int codigo) {
        return new SQLException("ORA-" + codigo + " detalle secreto", "72000", codigo);
    }

    private static CrearUsuarioDTO solicitud() {
        return new CrearUsuarioDTO("Ana", "ana", "GERENTE", null, "clave-nueva-1");
    }

    private final class UsuarioDAOFalso extends UsuarioDAO {
        @Override public Optional<Long> buscarIdRolActivo(Connection connection, String nombre) {
            eventos.add("rol");
            return Optional.of(2L);
        }
        @Override public long crear(Connection connection, String nombre, String username,
                long idRol, Long idSucursal) {
            eventos.add("crear");
            return 7;
        }
        @Override public Optional<UsuarioDTO> buscarPorId(Connection connection, long id) {
            eventos.add("leer");
            return Optional.of(new UsuarioDTO(id, "Ana", "ana", "GERENTE", null, null,
                    "ACTIVO", null, null, false, true));
        }
        @Override public Pagina<UsuarioDTO> listar(Connection connection, int pagina,
                int tamano, String rol, String estado, Long idSucursal) {
            return new Pagina<>(List.of(), 0, pagina, tamano);
        }
    }

    private final class SeguridadDAOFalso extends SeguridadDAO {
        SQLException error;
        long idUsuario;
        @Override public void establecerClave(Connection connection, long id, String algoritmo,
                int iteraciones, String salt, String huella) throws SQLException {
            eventos.add("clave");
            if (error != null) throw error;
            idUsuario = id;
        }
    }
}
