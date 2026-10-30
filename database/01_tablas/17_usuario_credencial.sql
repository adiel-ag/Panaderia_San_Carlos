-- Credencial de acceso de cada usuario (paso 2: inicio de sesion).
-- Separada de USUARIO para que los datos sensibles no se mezclen con la tabla
-- que consultan los demas modulos; USUARIO y sus paquetes no se modifican.
--
-- La contrasena nunca se guarda: solo su huella PBKDF2, calculada en Java con
-- un salt aleatorio por usuario. Algoritmo e iteraciones se guardan para poder
-- reforzar el calculo en el futuro sin invalidar las claves existentes.

CREATE TABLE USUARIO_CREDENCIAL (
    id_usuario NUMBER NOT NULL,
    algoritmo VARCHAR2(30) NOT NULL,
    iteraciones NUMBER(10) NOT NULL,
    salt VARCHAR2(64) NOT NULL,
    clave_hash VARCHAR2(128) NOT NULL,
    intentos_fallidos NUMBER(3) DEFAULT 0 NOT NULL,
    bloqueado_hasta TIMESTAMP,
    fecha_cambio TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ultimo_acceso TIMESTAMP,

    CONSTRAINT pk_usuario_credencial
        PRIMARY KEY (id_usuario),

    CONSTRAINT fk_usuario_cred_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES USUARIO(id_usuario),

    CONSTRAINT ck_usuario_cred_algoritmo
        CHECK (algoritmo IN ('PBKDF2WithHmacSHA256')),

    CONSTRAINT ck_usuario_cred_iteraciones
        CHECK (iteraciones > 0),

    CONSTRAINT ck_usuario_cred_intentos
        CHECK (intentos_fallidos >= 0)
);
