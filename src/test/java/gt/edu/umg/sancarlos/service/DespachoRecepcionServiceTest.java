package gt.edu.umg.sancarlos.service;

import gt.edu.umg.sancarlos.dao.DespachoDAO;
import gt.edu.umg.sancarlos.exception.DataAccessException;
import gt.edu.umg.sancarlos.exception.DespachoConflictoException;
import gt.edu.umg.sancarlos.exception.DespachoNoEncontradoException;
import gt.edu.umg.sancarlos.model.Despacho;
import gt.edu.umg.sancarlos.model.UsuarioSesion;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Recepción sin Oracle: controla bloqueo, alcance y reversión. */
class DespachoRecepcionServiceTest {
    private final List<String> eventos = new ArrayList<>();
    private final DaoFalso dao = new DaoFalso();
    private final UsuarioSesion sucursal = new UsuarioSesion(7, "s", "Sucursal", "SUCURSAL", 3L);

    @Test void recibeYAnexaObservacionEnUnaTransaccion() {
        Despacho resultado = servicio().recibirPedido(50, sucursal, "  faltan dos  ");
        assertEquals("ENTREGADO", resultado.getEstado());
        assertEquals("[Recibido: faltan dos]", dao.texto);
        assertEquals(List.of("abrir", "setAutoCommit", "bloquear", "actualizar", "buscar",
                "commit", "close"), eventos);
    }

    @Test void observacionVaciaQuedaConforme() {
        servicio().recibirPedido(50, sucursal, "   ");
        assertEquals("[Recibido conforme]", dao.texto);
    }

    @Test void despachoAusenteOSucursalAjenaDevuelven404() {
        dao.existe = false;
        assertEquals("El pedido no tiene despacho", assertThrows(DespachoNoEncontradoException.class,
                () -> servicio().recibirPedido(50, sucursal, null)).getMessage());
        eventos.clear(); dao.existe = true;
        UsuarioSesion ajena = new UsuarioSesion(8, "a", "Ajena", "SUCURSAL", 4L);
        assertThrows(DespachoNoEncontradoException.class,
                () -> servicio().recibirPedido(50, ajena, null));
        assertFalse(eventos.contains("actualizar"));
    }

    @Test void entregadoUOtroEstadoSonConflictos() {
        dao.estado = "ENTREGADO";
        assertEquals("Este despacho ya fue recibido", assertThrows(DespachoConflictoException.class,
                () -> servicio().recibirPedido(50, sucursal, null)).getMessage());
        dao.estado = "PENDIENTE";
        assertThrows(DespachoConflictoException.class,
                () -> servicio().recibirPedido(50, sucursal, null));
        assertFalse(eventos.contains("actualizar"));
    }

    @Test void observacionLargaSeRechazaAntesDeAbrir() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio().recibirPedido(50, sucursal, "x".repeat(501)));
        assertTrue(eventos.isEmpty());
    }

    @Test void errorAlActualizarRevierte() {
        dao.error = new SQLException("fallo", "42000", 942);
        assertThrows(DataAccessException.class,
                () -> servicio().recibirPedido(50, sucursal, null));
        assertTrue(eventos.contains("rollback"));
        assertFalse(eventos.contains("commit"));
    }

    private DespachoService servicio() { return new DespachoService(dao, this::conexion); }
    private Connection conexion() {
        eventos.add("abrir");
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (List.of("setAutoCommit", "commit", "rollback", "close").contains(method.getName()))
                        eventos.add(method.getName());
                    return null;
                });
    }
    private final class DaoFalso extends DespachoDAO {
        boolean existe = true;
        String estado = "DESPACHADO";
        String texto;
        SQLException error;
        @Override public Optional<DespachoBloqueado> bloquearPorPedido(Connection c, long id) {
            eventos.add("bloquear");
            return existe ? Optional.of(new DespachoBloqueado(estado, 3)) : Optional.empty();
        }
        @Override public void registrarRecepcion(Connection c, long id, String texto) throws SQLException {
            eventos.add("actualizar");
            if (error != null) throw error;
            this.texto = texto;
            estado = "ENTREGADO";
        }
        @Override public Optional<Despacho> buscarPorPedido(Connection c, long id) {
            eventos.add("buscar");
            Despacho despacho = new Despacho(); despacho.setEstado(estado);
            return Optional.of(despacho);
        }
    }
}
