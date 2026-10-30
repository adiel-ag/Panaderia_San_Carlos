package gt.edu.umg.sancarlos.dto;

/** Contrasena actual y nueva del usuario autenticado. */
public record CambiarClaveDTO(String claveActual, String claveNueva) {
}
