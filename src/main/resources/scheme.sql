--- Elimina las tablas si ya existen para asegurar un inicio limpio (El orden de eliminación importa para evitar conflictos de Foreign Keys)
DROP TABLE IF EXISTS professors;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS persons;

-- 1. Crea la tabla 'persons' PRIMERO, ya que es la entidad fuerte de la que dependen las demás
CREATE TABLE persons(
    id_per INTEGER PRIMARY KEY AUTOINCREMENT,
    dni INTEGER NOT NULL UNIQUE,
    mail VARCHAR(250) NOT NULL UNIQUE,
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
    FOREIGN KEY (id_prof) REFERENCES persons(id_per) ON DELETE CASCADE
);
-- Elimina la tabla 'alumnos' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS alumnos;

-- Crea la tabla 'alumnos' con los campos especificados 
CREATE TABLE alumnos(
    id_alumno INTEGER PRIMARY KEY,
    legajo INTEGER NOT NULL UNIQUE,
    ingresante BOOLEAN,
    FOREIGN KEY (id_alumno) REFERENCES persons(id_per) ON DELETE CASCADE
);

-- Elimina la tabla 'materias' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS materias;

-- Crea la tabla 'materias' con los campos especificados 
CREATE TABLE materias(
    id_materia INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo VARCHAR(100) NOT NULL UNIQUE,
    nombre VARCHAR (100) NOT NULL,
    carga_horaria INTEGER,
    periodo TEXT
);

-- Elimina la tabla 'comisiones' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS comisiones;

-- Crea la tabla 'comisiones' con los campos especificados 
CREATE TABLE comisiones(
    id_comision INTEGER PRIMARY KEY AUTOINCREMENT,
    horario TEXT,
    id_materia INTEGER,
    FOREIGN KEY (id_materia) REFERENCES materias(id_materia)
);

-- Elimina la tabla 'docente_comision' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS docente_comision;

-- Crea la tabla 'docente_comision' con los campos especificados 
CREATE TABLE docente_comision(
    id_prof INTEGER,
    id_comision INTEGER,
    PRIMARY KEY (id_prof, id_comision),
    FOREIGN KEY (id_prof) REFERENCES professors(id_prof),
    FOREIGN KEY (id_comision) REFERENCES comisiones(id_comision)
);
