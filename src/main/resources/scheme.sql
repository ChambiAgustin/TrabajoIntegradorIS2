-- Elimina la tabla 'users' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS users;

-- Crea la tabla 'users' con los campos originales, adaptados para SQLite
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- Clave primaria autoincremental para SQLite
    name TEXT NOT NULL UNIQUE,          -- Nombre de usuario (TEXT es el tipo de cadena recomendado para SQLite), con restricción UNIQUE
    password TEXT NOT NULL           -- Contraseña hasheada (TEXT es el tipo de cadena recomendado para SQLite)
);

--- Elimina la tabla 'persons' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS persons;

-- Crea la tabla 'persons' con los campos especificados
CREATE TABLE persons(
    id_per INTEGER PRIMARY KEY AUTOINCREMENT,
    dni INTEGER NOT NULL UNIQUE,
    mail VARCHAR(250) NOT NULL,
    tel INTEGER NOT NULL,
    nombre VARCHAR(250) NOT NULL,
    apellido VARCHAR(250) NOT NULL,
    direccion VARCHAR(250) NOT NULL
);

--Elimina la tabla 'professor' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS professors;

-- Crea la tabla 'professor' con los campos especificados
CREATE TABLE professors(
    id_prof INTEGER PRIMARY KEY,
    legajo INTEGER NOT NULL,
    cargo VARCHAR(250) NOT NULL,
    CONSTRAINT fk_prof FOREIGN KEY (id_prof) REFERENCES persons(id_per) ON DELETE CASCADE
);

