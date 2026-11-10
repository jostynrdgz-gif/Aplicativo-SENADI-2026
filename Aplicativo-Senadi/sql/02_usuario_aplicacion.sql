-- Ejecutar manualmente como administrador en MySQL Workbench.
-- Reemplazar el marcador por una contraseña propia antes de ejecutar.
-- No guardar ni publicar este archivo una vez escrita la contraseña.
CREATE USER 'senadi_app'@'localhost' IDENTIFIED BY 'ESCRIBE_AQUI_TU_CLAVE';
GRANT SELECT, INSERT, CREATE, REFERENCES ON senadi_aplicativo.* TO 'senadi_app'@'localhost';
SHOW GRANTS FOR 'senadi_app'@'localhost';
