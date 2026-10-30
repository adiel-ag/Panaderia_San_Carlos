package gt.edu.umg.sancarlos.resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** HttpServletRequest y HttpSession en memoria, sin Tomcat. */
final class PeticionFalsa {

    /** Todas las sesiones creadas, en orden; la ultima es la actual si sigue valida. */
    final List<SesionFalsa> sesiones = new ArrayList<>();
    private SesionFalsa actual;

    final HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
            HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getSession")) {
                    boolean crear = args == null || (Boolean) args[0];
                    if ((actual == null || actual.invalidada) && crear) {
                        actual = new SesionFalsa();
                        sesiones.add(actual);
                    }
                    return actual == null || actual.invalidada ? null : actual.session;
                }
                return null;
            });

    SesionFalsa sesionActual() {
        return actual == null || actual.invalidada ? null : actual;
    }

    static final class SesionFalsa {

        final Map<String, Object> atributos = new HashMap<>();
        boolean invalidada;

        final HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "invalidate" -> {
                            if (invalidada) {
                                throw new IllegalStateException("ya invalidada");
                            }
                            invalidada = true;
                            return null;
                        }
                        case "setAttribute" -> {
                            atributos.put((String) args[0], args[1]);
                            return null;
                        }
                        case "getAttribute" -> {
                            if (invalidada) {
                                throw new IllegalStateException("sesion invalidada");
                            }
                            return atributos.get((String) args[0]);
                        }
                        default -> {
                            return null;
                        }
                    }
                });
    }
}
