# Aplicativo-Senadi

Aplicación web Java 21 con Spring Boot 3.5.7, Maven, Thymeleaf, JDBC, MySQL y Apache POI. Proyecto preparado para abrir desde NetBeans usando este `pom.xml`.

## Puesta en marcha

1. Tener MySQL funcionando en localhost:3306 y Java JDK 21.
2. Ejecutar `sql/01_crear_base.sql` desde MySQL Workbench. Crear el usuario local con los permisos indicados en el archivo.
3. Copiar `application-local.properties.example` a `application-local.properties` y completar la contraseña local. La copia con credenciales está excluida de Git.
4. Desde la carpeta que contiene `pom.xml`, ejecutar `mvn spring-boot:run` o el goal `spring-boot:run` desde NetBeans. No necesita GlassFish: incluye Tomcat.
5. Abrir http://localhost:8080 . La aplicación solo escucha en 127.0.0.1 mientras no existe login.

También se aceptan las variables `DB_URL`, `DB_USER`, `DB_PASSWORD`. El archivo local tiene prioridad. Sin MySQL disponible, el arranque falla; no se simula persistencia.

## Importación

- Botón **Importar Excel**, archivos `.xls` y `.xlsx`, primera hoja, encabezados en fila 1.
- Máximos: 10 MB, 10 000 filas de datos, 100 columnas, 10 000 caracteres por celda.
- Encabezados obligatorios y únicos sin distinguir mayúsculas. Se rechazan fórmulas, celdas con error, celdas combinadas y datos fuera de los encabezados.
- Filas vacías omitidas; valores almacenados como texto formateado para preservar códigos y fechas visibles. Para cédulas, dar formato Texto en Excel antes de introducirlas.
- Cada envío crea un lote; subir el mismo archivo dos veces crea dos lotes. No actualiza registros anteriores.
- Guarda metadatos en `excel_importacion` y cada fila como arreglo JSON de texto en `excel_fila`. No crea tablas SQL arbitrarias a partir del Excel ni modifica tablas de negocio existentes.
- Todas las inserciones se ejecutan en una transacción: si falla alguna fila, no queda un lote parcial.
- La vista muestra 200 filas por lote y los últimos 100 lotes. Los datos se escapan al mostrar para evitar interpretar HTML.

## Estado y próximos pasos

El login aún no está implementado. Integrarlo al recibir el script; proteger consultas e importaciones y añadir CSRF con Spring Security antes de exponer la aplicación fuera de la computadora. No se debe publicar esta versión de desarrollo como servicio abierto.

Pruebas automáticas: `mvn test`. Usan H2 en modo MySQL para comprobar parser, persistencia y rollback; no sustituyen la prueba real con MySQL. La conexión real requiere configurar las credenciales locales. Después se hará la prueba solicitada con el login y un dato de prueba.

Los archivos Jakarta del arquetipo original se conservan para referencia, excluidos de la compilación; la aplicación activa está en `ec.senadi`. El empaquetado activo es JAR ejecutable con servidor web incluido.
