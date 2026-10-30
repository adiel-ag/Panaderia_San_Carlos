package gt.edu.umg.sancarlos.util;

import gt.edu.umg.sancarlos.service.AutenticacionService;
import java.io.Console;
import java.util.Arrays;

/**
 * Asigna la contrasena de un usuario desde la consola. Sirve para la primera
 * clave del ADMINISTRADOR, cuando todavia nadie puede iniciar sesion.
 * La contrasena se escribe sin mostrarse y nunca se pasa como argumento.
 *
 * Uso, desde la carpeta del proyecto y con DB_URL, DB_USER y DB_PASSWORD:
 * mvn -q compile exec:java -Dexec.mainClass=gt.edu.umg.sancarlos.util.AsignarClave -Dexec.args=USUARIO
 */
public final class AsignarClave {

    private AsignarClave() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            System.err.println("Uso: AsignarClave <usuario>");
            System.exit(2);
        }
        Console consola = System.console();
        if (consola == null) {
            System.err.println("No hay una consola interactiva. Ejecuta el comando desde una "
                    + "terminal (cmd o PowerShell), no desde la ventana Output de NetBeans.");
            System.exit(2);
        }
        String username = args[0].trim();
        char[] clave = consola.readPassword("Nueva contraseña para %s: ", username);
        char[] confirmacion = consola.readPassword("Repite la contraseña: ");
        try {
            if (clave == null || confirmacion == null || !Arrays.equals(clave, confirmacion)) {
                System.err.println("Las contraseñas no coinciden. No se cambió nada.");
                System.exit(1);
            }
            new AutenticacionService().establecerClave(username, clave);
            System.out.println("Contraseña asignada a " + username + ".");
        } catch (RuntimeException exception) {
            System.err.println("No se asignó la contraseña: " + exception.getMessage());
            System.exit(1);
        } finally {
            if (clave != null) {
                Arrays.fill(clave, '\0');
            }
            if (confirmacion != null) {
                Arrays.fill(confirmacion, '\0');
            }
        }
    }
}
