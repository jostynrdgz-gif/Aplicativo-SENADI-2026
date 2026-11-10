-- Ejecutar en MySQL Workbench con una cuenta administradora.
CREATE DATABASE IF NOT EXISTS senadi_aplicativo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Crear el usuario senadi_app desde Administración > Users and Privileges,
-- con una contraseña propia y permisos SELECT, INSERT, CREATE, REFERENCES
-- únicamente sobre senadi_aplicativo.*. No guardar contraseñas en este repositorio.
