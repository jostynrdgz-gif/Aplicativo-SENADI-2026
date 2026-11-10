# Manual técnico

**Aplicativo SENADI — versión inicial, avance 1**

## 1. Objetivo y alcance

Sistema web independiente para el proyecto de pasantías de SENADI. El avance implementa importación genérica de Excel, persistencia y consulta. Aún no clasifica archivos como POA/eSIGEF, no combina matrices y no implementa login, roles, filtros ni unión de PDF.

## 2. Tecnologías

| Componente | Implementación |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.7 |
| Construcción | Maven |
| Web | Spring MVC, Thymeleaf, Tomcat embebido |
| Persistencia | Spring JDBC / JdbcTemplate, MySQL Connector/J |
| Excel | Apache POI 5.4.1 |
| Base real | MySQL 8.0, `senadi_aplicativo` |
| Pruebas | JUnit, Spring Boot Test, H2 en modo MySQL |

## 3. Organización

- `src/main/java/ec/senadi/AplicativoSenadi.java`: entrada de Spring Boot.
- `ExcelReader.java`: lectura y validación de la primera hoja.
- `ImportacionService.java`: transacción, inserciones, historial y consulta.
- `ImportacionController.java`: rutas web y mensajes.
- `src/main/resources/templates/`: vistas `inicio.html` y `detalle.html`.
- `src/main/resources/static/estilos.css`: presentación.
- `src/main/resources/schema.sql`: creación de las tablas si no existen.
- `src/test/java/ec/senadi/ImportacionTest.java`: seis pruebas de importación.
- `sql/`: preparación de base y plantilla de usuario local.

El proyecto se empaqueta como JAR con servidor incluido. Los archivos Jakarta del arquetipo original se conservan como referencia, excluidos de la compilación y recursos activos según `pom.xml`.

## 4. Instalación de desarrollo

1. Instalar JDK 21, Maven compatible y MySQL 8.0.
2. Ejecutar `sql/01_crear_base.sql` con una cuenta administradora. Crear `senadi_app` para `localhost` con contraseña propia, usando la plantilla `sql/02_usuario_aplicacion.sql` sin guardar la contraseña en Git.
3. Conceder SELECT, INSERT, CREATE y REFERENCES únicamente sobre `senadi_aplicativo.*`. No se requiere usar root desde la aplicación.
4. Copiar `application-local.properties.example` como `application-local.properties` junto al `pom.xml` y completar las credenciales locales.
5. Ejecutar desde esa carpeta `mvn spring-boot:run`. En NetBeans puede ejecutarse el goal `spring-boot:run`.
6. Abrir `http://localhost:8080`.

Configuración admitida: `DB_URL`, `DB_USER`, `DB_PASSWORD` y `PORT`. El archivo externo `application-local.properties` puede reemplazar los valores de conexión. La base debe existir antes del arranque. Sin conexión real, el arranque falla.

## 5. Datos y transacciones

| Tabla | Campos principales | Uso |
|---|---|---|
| `excel_importacion` | id, archivo, hoja, encabezados, filas, creado | Metadatos del lote; encabezados como arreglo JSON de texto. |
| `excel_fila` | id, importacion_id, numero_fila, valores | Cada fila como arreglo JSON de texto; referencia al lote. |

Los arreglos respetan el orden de columnas. Los valores se guardan como texto formateado mediante DataFormatter. No se generan nombres de tablas SQL a partir de encabezados. Se utilizan parámetros SQL y claves generadas. La importación está marcada con `@Transactional(rollbackFor = Exception.class)`.

## 6. Rutas

| Método | Ruta | Función |
|---|---|---|
| GET | `/` | Últimos 100 lotes. |
| POST multipart | `/importaciones` | Campo `archivo`; valida, guarda y redirige al detalle. |
| GET | `/importaciones/{id}` | Metadatos y primeras 200 filas del lote. |

El parser rechaza encabezados duplicados o vacíos, fórmulas, celdas con error, celdas combinadas y datos fuera del encabezado. Límites: 10 MB, 10 000 filas, 100 columnas y 10 000 caracteres por celda. Cada envío genera un lote independiente.

## 7. Verificación realizada

Compilación Maven correcta y seis pruebas aprobadas: XLSX preservando ceros y omitiendo vacíos; XLS; encabezados duplicados; fórmulas; rollback ante fallo de inserción; contenido inválido con extensión Excel.

Las pruebas usan H2 en modo MySQL. Además se verificaron la creación de tablas InnoDB, el arranque con conexión real MySQL y la página principal consultando historial vacío. La importación de un archivo completo a MySQL desde el navegador y la prueba de login siguen pendientes.

Ejecutar `mvn test` y `mvn package` desde la carpeta del proyecto. El paquete se genera en `target/Aplicativo-Senadi-1.0-SNAPSHOT.jar`.

## 8. Seguridad y despliegue pendiente

La aplicación solo escucha en `127.0.0.1`; el avance no implementa autenticación, autorización ni CSRF. Antes de desplegar deben integrarse controles de acceso del lado de Java según roles definitivos. Se deben configurar HTTPS, dominio, proxy inverso, credenciales externas, copias de seguridad y recuperación según el servidor institucional. Sistema operativo y política de autenticación institucional aún por confirmar.

Los archivos `application-local.properties`, `.env`, los binarios y logs se excluyen de Git. Nunca publicar contraseñas, archivos de recuperación de MySQL ni matrices institucionales reales como datos de prueba.

## 9. Evolución de los manuales

Actualizar este documento y el manual de usuario en cada entrega. La versión final debe incluir roles aprobados, reglas y columnas POA/eSIGEF, combinación de matrices, unión de PDF, instalación institucional, respaldo, mantenimiento, capturas y resultados de pruebas de aceptación.
