package gt.edu.umg.sancarlos.dto;

import java.time.LocalDateTime;

/** Datos publicos de administracion del usuario. */
public record UsuarioDTO(long idUsuario, String nombre, String username, String rol,
        Long idSucursal, String nombreSucursal, String estado,
        LocalDateTime fechaCreacion, LocalDateTime ultimoAcceso,
        boolean bloqueado, boolean tieneClave) {
}
