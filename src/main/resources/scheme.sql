--- Elimina las tablas si ya existen para asegurar un inicio limpio (El orden de eliminación importa para evitar conflictos de Foreign Keys)
DROP TABLE IF EXISTS professors;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS persons;

-- 1. Crea la tabla 'persons' PRIMERO, ya que es la entidad fuerte de la que dependen las demás
CREATE TABLE persons(
    id_per INTEGER PRIMARY KEY AUTOINCREMENT,
    dni INTEGER NOT NULL UNIQUE,
    mail VARCHAR(250) NOT NULL,
    tel INTEGER NOT NULL,
    nombre VARCHAR(250) NOT NULL,
    apellido VARCHAR(250) NOT NULL,
    direccion VARCHAR(250) NOT NULL
);

-- 2. Crea la tabla 'users' vinculada a persons
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,          
    password TEXT NOT NULL,
    -- Agregamos el rol para poder autorizar accesos en el Spark Router
    tipo_usuario TEXT NOT NULL CHECK(tipo_usuario IN ('ADMINISTRADOR', 'ALUMNO', 'DOCENTE', 'DIRECTIVO')),
    -- Relación 1 a 1 con la persona
    person_id INTEGER UNIQUE,
    CONSTRAINT fk_user_person FOREIGN KEY (person_id) REFERENCES persons(id_per) ON DELETE CASCADE
);

-- 3. Crea la tabla 'professors' vinculada a persons
CREATE TABLE professors(
    id_prof INTEGER PRIMARY KEY, -- Su ID principal es directamente el de la persona
    legajo INTEGER NOT NULL UNIQUE,
    cargo VARCHAR(250) NOT NULL,
    CONSTRAINT fk_prof FOREIGN KEY (id_prof) REFERENCES persons(id_per) ON DELETE CASCADE
);
