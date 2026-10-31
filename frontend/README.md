# Frontend

Para desarrollar, inicia Tomcat en `http://localhost:8080`, ejecuta `npm ci` y `npm run dev` en esta carpeta y abre `http://localhost:5173`. Vite envía `/api` a Tomcat.

Desde la raíz del proyecto, `mvn package` instala Node y las dependencias, ejecuta las pruebas y compila el frontend. Maven coloca `index.html` y `assets/` en la raíz del WAR.

Cada compilación genera archivos con un nombre nuevo en `assets/`; los anteriores quedan en la carpeta del WAR sin usarse. Para quitarlos, usa `mvn clean package`, que conviene antes de entregar el WAR. No se borran automáticamente porque Tomcat sirve esa carpeta en vivo.

Detén `npm run dev` antes de ejecutar `mvn package`: en Windows el servidor de desarrollo mantiene abiertos archivos de `node_modules` y `npm ci` no puede reinstalarlos.

Para compilar solo el backend, usa `mvn package -DskipFrontend`. El WAR lleva entonces el último frontend que quedó en `target/frontend` (ninguno después de `mvn clean`).
