-- Ejecutar UNA VEZ como root en la base existente, antes de arrancar Java.
-- No elimina importaciones ni cambia las credenciales de MySQL.
USE senadi_aplicativo;
ALTER TABLE excel_importacion
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'GENERICO',
    ADD COLUMN subido_por VARCHAR(80) NULL;
CREATE INDEX idx_excel_tipo_id ON excel_importacion(tipo, id);

-- Catálogo de roles futuros. El login actual sigue usando usuarios en memoria.
INSERT INTO app_rol(nombre, descripcion)
SELECT 'FINANCIERO', 'Consulta de la matriz POA publicada por Planificación'
WHERE NOT EXISTS (SELECT 1 FROM app_rol WHERE nombre = 'FINANCIERO');
