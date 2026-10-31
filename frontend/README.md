# Frontend

Para desarrollar, inicia Tomcat en `http://localhost:8080`, ejecuta `npm ci` y `npm run dev` en esta carpeta y abre `http://localhost:5173`. Vite envía `/api` a Tomcat.

Desde la raíz del proyecto, `mvn package` instala Node y las dependencias, ejecuta las pruebas y compila el frontend. Maven coloca `index.html` y `assets/` en la raíz del WAR.

Para compilar solo el backend, usa `mvn package -DskipFrontend`. El WAR lleva entonces el último frontend que quedó en `target/frontend` (ninguno después de `mvn clean`).
