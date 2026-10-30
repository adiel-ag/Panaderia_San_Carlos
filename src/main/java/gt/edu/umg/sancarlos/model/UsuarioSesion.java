package gt.edu.umg.sancarlos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.security.Principal;

/** Usuario autenticado tal como lo ven la API y los permisos por rol. */
public class UsuarioSesion implements Principal {

    private final long idUsuario;
    private final String username;
    private final String nombre;
    private final String rol;
    private final Long idSucursal;

    public UsuarioSesion(long idUsuario, String username, String nombre, String rol,
            Long idSucursal) {
        this.idUsuario = idUsuario;
        this.username = username;
        this.nombre = nombre;
        this.rol = rol;
        this.idSucursal = idSucursal;
    }

    public long getIdUsuario() { return idUsuario; }
    public String getUsername() { return username; }
    public String getNombre() { return nombre; }
    public String getRol() { return rol; }
    /** Nulo para roles sin sucursal, como ADMINISTRADOR. */
    public Long getIdSucursal() { return idSucursal; }

    /** Exigido por Principal; no se publica en el JSON porque repite username. */
    @Override
    @JsonIgnore
    public String getName() {
        return username;
    }
}
