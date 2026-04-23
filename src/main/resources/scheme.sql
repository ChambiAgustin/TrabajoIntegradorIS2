PRAGMA foreign_keys = ON;
--- Elimina la tabla 'persons' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS persons;

-- Crea la tabla 'persons' con los campos especificados
CREATE TABLE persons(
    id_per INTEGER PRIMARY KEY AUTOINCREMENT,
    dni INTEGER NOT NULL UNIQUE,
    mail VARCHAR(250) NOT NULL UNIQUE,
    tel INTEGER NOT NULL,
    nombre VARCHAR(250) NOT NULL,
    apellido VARCHAR(250) NOT NULL,
    direccion VARCHAR(250) NOT NULL
);

-- Elimina la tabla 'users' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS users;

-- Crea la tabla 'users' con los campos originales, adaptados para SQLite
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- Clave primaria autoincremental para SQLite
    name TEXT NOT NULL UNIQUE,          -- Nombre de usuario (TEXT es el tipo de cadena recomendado para SQLite), con restricción UNIQUE
    password TEXT NOT NULL,          -- Contraseña hasheada (TEXT es el tipo de cadena recomendado para SQLite)
    tipo_usuario TEXT CHECK(tipo_usuario IN ('ADMIN', 'ALUMNO', 'DOCENTE')) NOT NULL,
    id_persona INTEGER UNIQUE,
    FOREIGN KEY (id_persona) REFERENCES persons(id_per)
);


--Elimina la tabla 'professor' si ya existe para asegurar un inicio limpio
DROP TABLE IF EXISTS professors;

-- Crea la tabla 'professor' con los campos especificados
CREATE TABLE professors(
    id_prof INTEGER PRIMARY KEY,
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

-- Elimina la tabla 'inscripciones' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS inscripciones;

-- Crea la tabla 'inscripciones' con los campos especificados 
CREATE TABLE inscripciones(
    id_inscripcion INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha TEXT,
    id_alumno INTEGER,
    id_comision INTEGER,
    FOREIGN KEY (id_alumno) REFERENCES alumnos(id_alumno),
    FOREIGN KEY (id_comision) REFERENCES comisiones(id_comision)
);

-- Elimina la tabla 'evaluaciones' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS evaluaciones;

-- Crea la tabla 'evaluaciones' con los campos especificados 
CREATE TABLE evaluaciones(
    id_evaluacion INTEGER PRIMARY KEY AUTOINCREMENT,
    nota INTEGER,
    fecha TEXT,
    id_alumno INTEGER,
    id_comision INTEGER,
    FOREIGN KEY (id_alumno) REFERENCES alumnos(id_alumno),
    FOREIGN KEY (id_comision) REFERENCES comisiones(id_comision)
);

-- Elimina la tabla 'mensajes' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS mensajes;

-- Crea la tabla 'mensajes' con los campos especificados 
CREATE TABLE mensajes(
    id_mensaje INTEGER PRIMARY KEY AUTOINCREMENT,
    contenido TEXT,
    fecha TEXT,
    leido BOOLEAN,
    id_emisor INTEGER,
    id_receptor INTEGER,
    FOREIGN KEY (id_emisor) REFERENCES users(id),
    FOREIGN KEY (id_receptor) REFERENCES users(id)
);
-- Elimina la tabla 'notificaciones' si ya extiste para asegurar un inicio limpio
DROP TABLE IF EXISTS notificaciones;

-- Crea la tabla 'notificaciones' con los campos especificados 
CREATE TABLE notificaciones(
    id_notificacion INTEGER PRIMARY KEY AUTOINCREMENT,
    contenido TEXT,
    fecha TEXT,
    leida BOOLEAN,
    id_usuario INTEGER,
    FOREIGN KEY (id_usuario) REFERENCES users(id)
);
