# Publicación local con Cloudflare Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar el WAR de Panadería San Carlos en `https://panaderia.tarea.party/` desde esta computadora para usuarios con cuenta.

**Architecture:** Vue y la API se sirven desde `ROOT.war` en una instancia aislada de Tomcat 10.1. Cloudflare Tunnel lleva el hostname público al puerto 8081 local. Oracle permanece privado y se conecta mediante variables del proceso de Tomcat.

**Tech Stack:** Maven, Java 17, Tomcat 10.1, Vue 3/Vite, Oracle, Cloudflare Tunnel en Windows.

**Spec:** `docs/superpowers/specs/2026-10-31-publicacion-cloudflare-diseno.md`

## Global Constraints

- Dominio: `panaderia.tarea.party` sin tilde; acceso para cualquier persona con cuenta.
- Publicación inicial local, aprobada por el usuario; no se contrata ni aprovisiona VPS.
- Mantener Oracle fuera de Internet y credenciales fuera del repositorio y de la salida de comandos.
- Conservar los cambios de UI existentes en la carpeta actual.
- No modificar DNS antes de que Tomcat, Oracle y el WAR actual funcionen localmente.

## Review Focus

- La cookie de sesión en HTTPS lleva `Secure`, `HttpOnly` y `SameSite=Strict`.
- La compilación de producción conserva el WAR de desarrollo funcional por HTTP.
- El despliegue en `/` permite que `api/` y las rutas Vue funcionen en el mismo origen.
- Una sesión ausente no permite consultar datos privados.
- Al reiniciar la computadora, el origen y el túnel se recuperan sin intervención manual.

---

### Task 1: Preparar artefacto de producción

**Files:** `pom.xml`, `src/main/webapp/WEB-INF/web.xml`, `src/main/webapp/WEB-INF/web-produccion.xml`, prueba de configuración Maven en `scripts/` si aplica.

**Interfaces:** Perfil Maven `produccion` genera WAR con cookie de sesión `Secure` y conserva el empaquetado Vue/API.

- [x] `scripts/verificar-war-produccion.ps1` falló inicialmente por falta de `Secure=true`.
- [x] Descriptor y perfil Maven `produccion` añadidos; el descriptor local conserva HTTP para desarrollo.
- [x] `mvn package -Pproduccion` terminó con 179 pruebas Java y 374 Vue; el script verificó el WAR generado.

### Task 2: Verificar Tomcat y Oracle localmente

**Files:** WAR compilado en `target/`; `scripts/preparar-origen-local.ps1`; instalación existente de Tomcat 10.1 fuera del repositorio.

**Interfaces:** `http://127.0.0.1:8081/`, `http://127.0.0.1:8081/api/health`, login y una consulta autorizada.

- [x] Tomcat 10.1.61 identificado; Oracle responde al login y a la consulta de recetas.
- [x] Instancia aislada `target/publicacion-tomcat` preparada en 127.0.0.1:8081 con solo `ROOT.war`. El `ROOT.xml` heredado de NetBeans se desactivó en la copia local porque ignoraba el WAR.
- [x] `/` y `/api/health`: 200; `/manager/html`: 404; `/api/auth/me` sin sesión: 401; login, recetas y logout: 200. `Set-Cookie` contiene Secure, HttpOnly y SameSite=Strict. La prueba local autenticada envió la cookie manualmente porque el origen usa HTTP; el sitio público usará HTTPS.

### Task 3: Conectar Cloudflare y verificar desde fuera

**Files:** credenciales/configuración del túnel fuera del repositorio, dominio Cloudflare.

**Interfaces:** hostname `panaderia.tarea.party` → `http://127.0.0.1:8081` en el túnel.

- [ ] Autenticar la cuenta propietaria en Cloudflare sin registrar tokens en el proyecto.
- [ ] Crear el túnel nombrado y publicar el hostname; comprobar resolución DNS y certificado.
- [ ] Verificar desde una red externa portada, API, login, cookie y cierre de sesión.
- [ ] Configurar arranque automático y recuperación de Tomcat/túnel; registrar operación y rollback.

## Estado

Diseño aprobado; tareas 1 y 2 verificadas. Cloudflare pendiente de autorización en navegador.
