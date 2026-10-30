package gt.edu.umg.sancarlos.dto;

/** Datos para crear un usuario y su primera contrasena. */
public record CrearUsuarioDTO(String nombre, String username, String rol,
        Long idSucursal, String password) {
}
