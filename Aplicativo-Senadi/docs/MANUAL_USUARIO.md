# Manual de usuario

**Aplicativo SENADI — versión inicial, avance 1**

Este documento describe las funciones implementadas. Los pasos de login, roles, filtros, combinación de matrices y unión de PDF se documentarán cuando estén disponibles y probados.

## 1. Acceso

El responsable técnico debe mantener la aplicación y MySQL en ejecución. En la computadora de desarrollo, abrir `http://localhost:8080` en Chrome. La dirección institucional definitiva está pendiente de despliegue. La versión actual no solicita login.

## 2. Preparar el archivo Excel

1. Guardar el archivo en formato `.xls` o `.xlsx`.
2. Colocar la tabla en la primera hoja; únicamente esa hoja se procesa.
3. Escribir encabezados únicos, sin vacíos, en la fila 1.
4. Separar celdas combinadas y convertir fórmulas a valores.
5. Verificar que cada columna con datos tenga encabezado.
6. Mantener el archivo dentro de 10 MB, 10 000 filas de datos y 100 columnas. Cada celda admite hasta 10 000 caracteres.

Para conservar ceros iniciales en códigos o cédulas, introducirlos como texto en Excel. Revisar los datos antes de importar. Las filas vacías se omiten.

## 3. Importar

1. En la sección **Importar Excel**, pulsar **Archivo Excel** y seleccionar el documento.
2. Pulsar **Importar Excel**.
3. Si el proceso termina correctamente, aparece la confirmación y la vista del lote guardado.
4. Si aparece un error, revisar el mensaje y corregir el archivo antes de intentar otra vez.

Cada envío crea un lote nuevo. Importar dos veces el mismo archivo crea dos lotes; no actualiza ni reemplaza el anterior. Si falla una inserción, no queda una importación parcial.

## 4. Consultar

La página principal muestra las últimas 100 importaciones con archivo, hoja, cantidad de filas y fecha. Pulsar **Ver datos** para consultar un lote. Se muestran las primeras 200 filas; el resto permanece almacenado. Pulsar **Volver a importar** para regresar.

## 5. Mensajes frecuentes

| Mensaje o situación | Acción |
|---|---|
| Archivo excede 10 MB | Reducir o dividir el archivo. |
| Encabezados vacíos o repetidos | Corregir la fila 1. |
| Fórmulas, celdas combinadas o con errores | Convertir a valores, separar celdas y corregir errores. |
| Datos sin encabezado | Completar los encabezados correspondientes. |
| No hay filas de datos | Añadir datos debajo de la cabecera. |
| No se pudo importar / página no disponible | Contactar al responsable técnico para revisar MySQL y la ejecución de la aplicación. |

## 6. Funciones previstas

Los permisos por departamento todavía no están activos. Se prevé que Administrativo importe y consulte con filtros sin editar datos; Planificación podrá importar, combinar matrices y unir PDF. No se debe asumir que estas restricciones ya existen en el avance 1.

## 7. Validación pendiente

La conexión real a MySQL y el arranque web se verificaron. La prueba de importación de extremo a extremo se realizará después de integrar el script de login y el dato de prueba acordado. Se añadirán capturas y los resultados de esa prueba al manual final.
