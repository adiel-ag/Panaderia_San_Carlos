# Publicación de Panadería San Carlos en Cloudflare

## Objetivo

Abrir el sistema en `https://panaderia.tarea.party/` para personas que ya tienen una cuenta. La aplicación debe servir la interfaz Vue y la API Java desde el mismo origen, conservar las sesiones y operar con la base Oracle existente.

## Estado comprobado el 31 de octubre de 2026

- Maven empaqueta Vue y Java en un solo WAR. El WAR encontrado contiene `index.html`, `assets/`, `WEB-INF/web.xml` y la aplicación REST `/api`.
- El frontend usa rutas con `#` y solicitudes relativas a `api/`, por lo que funciona en la raíz del dominio sin CORS ni servidor frontend independiente.
- `cloudflared` 2026.10.0 está instalado en Windows. No se encontró configuración local de túnel en el perfil del usuario.
- `http://127.0.0.1:8080/api/health` no respondió durante la revisión. No hay origen disponible para publicar todavía.
- Oracle se configura con `DB_URL`, `DB_USER` y `DB_PASSWORD` en el proceso de Tomcat. El archivo `.env` del proyecto está ignorado por Git y sirve a scripts de prueba; no sustituye esas variables de Tomcat.
- La cookie de sesión es HttpOnly y SameSite=Strict. `web.xml` indica que falta `Secure` para producción HTTPS.
- El módulo de recetas conserva las fórmulas reales; ocho productos del catálogo actual aún necesitan sus cantidades. La refactorización visual tiene cambios locales pendientes, sin despliegue.

## Diseño de despliegue

```text
Navegador HTTPS
    → Cloudflare: panaderia.tarea.party
    → Cloudflare Tunnel
    → Tomcat en esta computadora: http://127.0.0.1:8081
    → Oracle (conexión privada desde Tomcat)
```

Se usará esta computadora para una primera publicación, porque ya tiene las herramientas y no se ha elegido VPS. Será una publicación de prueba: dependerá de que la computadora, Tomcat, Oracle y `cloudflared` permanezcan encendidos. Para disponibilidad continua, se migrará el mismo WAR y la base a un servidor administrado o VPS; el dominio podrá seguir apuntando al nuevo origen.

El WAR debe desplegarse como `ROOT.war` para que `/` sirva Vue y `/api/health` atienda la API. Se usará una instancia Tomcat aislada en `target/publicacion-tomcat`, con solo la aplicación raíz y el puerto `8081` ligado a `127.0.0.1`. Esto evita exponer las aplicaciones `manager`, `host-manager`, `docs` y `examples` que existen en la instalación de NetBeans, y evita competir por su puerto 8080. El túnel publicará el hostname `panaderia.tarea.party` hacia el puerto local 8081. Oracle no se expondrá mediante DNS ni puertos públicos. La cuenta de Cloudflare seguirá siendo administrada por su propietario.

## Requisitos previos a abrir el acceso público

1. Compilar desde el estado actual y ejecutar las pruebas Java y Vue; comprobar el WAR resultante.
2. Configurar la cookie de sesión con `Secure` en la variante de producción, manteniendo funcional el desarrollo local por HTTP.
3. Confirmar que las cuentas iniciales o de prueba no tienen contraseñas conocidas y conservar una copia recuperable de Oracle antes del despliegue.
4. Arrancar Oracle y Tomcat con las variables de entorno correctas; verificar localmente `/`, `/api/health`, inicio y cierre de sesión, y una consulta autenticada.
5. Conectar la cuenta Cloudflare, crear un túnel nombrado y asociar el hostname. La credencial del túnel debe quedar fuera del repositorio y sin imprimir su valor en registros.
6. Verificar desde otra red `https://panaderia.tarea.party/`, el certificado HTTPS, la cookie `Secure`/HttpOnly/SameSite, la carga de Vue, el login y los módulos autorizados.
7. Configurar arranque automático y recuperación de Tomcat y `cloudflared`; documentar cómo volver a la versión previa del WAR.

## Criterio de publicación

La página pública abre por HTTPS sin advertencias; el usuario válido entra; el no autenticado no accede a datos; Vue y `/api` funcionan bajo el mismo dominio; la base Oracle solo es accesible desde el servidor; una caída del origen se detecta y puede recuperarse.

## Decisiones y límites

- Dominio elegido por el usuario: `panaderia.tarea.party` (sin tilde).
- Acceso elegido por el usuario: cualquier persona con una cuenta del sistema; se conserva el login actual. Cloudflare Access puede añadirse después si cambia esa política.
- Esta computadora es la opción inicial propuesta por falta de VPS elegido, no una elección expresa de hospedaje permanente.
- No se cambiará DNS hasta comprobar el origen local y la seguridad de la sesión.
