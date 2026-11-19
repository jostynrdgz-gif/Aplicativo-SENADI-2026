-- Ejecutar en MySQL Workbench con una cuenta administradora.
USE senadi_aplicativo;

SET @senadi_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'excel_importacion'
              AND column_name = 'tipo'),
    'SELECT 1',
    'ALTER TABLE excel_importacion ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT ''GENERICO'''
);
PREPARE senadi_stmt FROM @senadi_sql;
EXECUTE senadi_stmt;
DEALLOCATE PREPARE senadi_stmt;

SET @senadi_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'excel_importacion'
              AND column_name = 'subido_por'),
    'SELECT 1',
    'ALTER TABLE excel_importacion ADD COLUMN subido_por VARCHAR(80) NULL'
);
PREPARE senadi_stmt FROM @senadi_sql;
EXECUTE senadi_stmt;
DEALLOCATE PREPARE senadi_stmt;

GRANT UPDATE (tipo) ON senadi_aplicativo.excel_importacion TO 'senadi_app'@'localhost';