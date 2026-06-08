package com.is1.proyecto.utils;

import com.is1.proyecto.models.*;
import org.mindrot.jbcrypt.BCrypt;
import org.javalite.activejdbc.Base;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Seeder {

    public static void seed() {
        if (User.count() > 0) {
            System.out.println("La base de datos ya contiene datos. Saltando seeder.");
            return;
        }

        System.out.println("Iniciando carga de datos de prueba (Seeders)...");

        try {
            // 1. ADMINISTRADOR
            Person pAdmin = new Person();
            pAdmin.setNombre("Admin"); 
            pAdmin.setApellido("Sistema"); 
            pAdmin.setMail("admin@puntodigital.gov.ar"); 
            pAdmin.setDni(11111111);
            pAdmin.setTel(123456);
            pAdmin.setDireccion("Calle Falsa 123");
            pAdmin.saveIt();
            
            User uAdmin = new User();
            uAdmin.setName("admin"); 
            uAdmin.setPassword(BCrypt.hashpw("admin123", BCrypt.gensalt())); 
            uAdmin.setTipoUsuario("ADMIN"); 
            uAdmin.setPersonId((Integer) pAdmin.getId()); 
            uAdmin.saveIt();

            // 2. PROFESORES
            Person pProf1 = new Person();
            pProf1.setNombre("Alan"); 
            pProf1.setApellido("Turing"); 
            pProf1.setMail("aturing@puntodigital.gov.ar"); 
            pProf1.setDni(22222222);
            pProf1.setTel(123456);
            pProf1.setDireccion("Calle Falsa 123");
            pProf1.saveIt();
            
            Professor prof1 = new Professor(); 
            prof1.setId(pProf1.getId()); 
            prof1.setLegajo(22222222); 
            prof1.setCargo("Titular"); 
            prof1.insert();
            
            User uProf1 = new User();
            uProf1.setName("22222222"); 
            uProf1.setPassword(BCrypt.hashpw("22222222", BCrypt.gensalt())); 
            uProf1.setTipoUsuario("DOCENTE"); 
            uProf1.setPersonId((Integer) pProf1.getId()); 
            uProf1.saveIt();

            Person pProf2 = new Person();
            pProf2.setNombre("Ada"); 
            pProf2.setApellido("Lovelace"); 
            pProf2.setMail("alovelace@puntodigital.gov.ar"); 
            pProf2.setDni(33333333);
            pProf2.setTel(123456);
            pProf2.setDireccion("Calle Falsa 123");
            pProf2.saveIt();
            
            Professor prof2 = new Professor(); 
            prof2.setId(pProf2.getId()); 
            prof2.setLegajo(33333333); 
            prof2.setCargo("JTP"); 
            prof2.insert();
            
            User uProf2 = new User();
            uProf2.setName("33333333"); 
            uProf2.setPassword(BCrypt.hashpw("33333333", BCrypt.gensalt())); 
            uProf2.setTipoUsuario("DOCENTE"); 
            uProf2.setPersonId((Integer) pProf2.getId()); 
            uProf2.saveIt();

            // 3. ALUMNOS
            Person pAlum1 = new Person();
            pAlum1.setNombre("Carlos"); 
            pAlum1.setApellido("Perez"); 
            pAlum1.setMail("cperez@alumno.com"); 
            pAlum1.setDni(44444444);
            pAlum1.setTel(123456);
            pAlum1.setDireccion("Calle Falsa 123");
            pAlum1.saveIt();
            
            Alumno alum1 = new Alumno();
            alum1.setId(pAlum1.getId());
            alum1.set("legajo", 44444444);
            alum1.set("ingresante", true);
            alum1.insert();
            
            User uAlum1 = new User();
            uAlum1.setName("44444444"); 
            uAlum1.setPassword(BCrypt.hashpw("44444444", BCrypt.gensalt())); 
            uAlum1.setTipoUsuario("ALUMNO"); 
            uAlum1.setPersonId((Integer) pAlum1.getId()); 
            uAlum1.saveIt();

            Person pAlum2 = new Person();
            pAlum2.setNombre("Maria"); 
            pAlum2.setApellido("Gomez"); 
            pAlum2.setMail("mgomez@alumno.com"); 
            pAlum2.setDni(55555555);
            pAlum2.setTel(123456);
            pAlum2.setDireccion("Calle Falsa 123");
            pAlum2.saveIt();
            
            Alumno alum2 = new Alumno();
            alum2.setId(pAlum2.getId());
            alum2.set("legajo", 55555555);
            alum2.set("ingresante", false);
            alum2.insert();
            
            User uAlum2 = new User();
            uAlum2.setName("55555555"); 
            uAlum2.setPassword(BCrypt.hashpw("55555555", BCrypt.gensalt())); 
            uAlum2.setTipoUsuario("ALUMNO"); 
            uAlum2.setPersonId((Integer) pAlum2.getId()); 
            uAlum2.saveIt();

            // 4. MATERIAS
            Materia mProg101 = new Materia(); mProg101.set("codigo", "PROG-101"); mProg101.set("nombre", "Introducción a la Programación"); mProg101.set("carga_horaria", 4); mProg101.set("periodo", "1° Cuatrimestre"); mProg101.set("anio", 1); mProg101.saveIt();
            Materia mMat101  = new Materia(); mMat101.set("codigo", "MAT-101");  mMat101.set("nombre", "Matemática Discreta");           mMat101.set("carga_horaria", 6);  mMat101.set("periodo", "1° Cuatrimestre"); mMat101.set("anio", 1);  mMat101.saveIt();
            
            Materia mProg102 = new Materia(); mProg102.set("codigo", "PROG-102"); mProg102.set("nombre", "Programación Orientada a Objetos"); mProg102.set("carga_horaria", 4); mProg102.set("periodo", "2° Cuatrimestre"); mProg102.set("anio", 1); mProg102.saveIt();
            Materia mMat102  = new Materia(); mMat102.set("codigo", "MAT-102");  mMat102.set("nombre", "Álgebra");                        mMat102.set("carga_horaria", 6);  mMat102.set("periodo", "2° Cuatrimestre"); mMat102.set("anio", 1);  mMat102.saveIt();

            Materia mProg201 = new Materia(); mProg201.set("codigo", "PROG-201"); mProg201.set("nombre", "Estructuras de Datos y Algoritmos"); mProg201.set("carga_horaria", 4); mProg201.set("periodo", "1° Cuatrimestre"); mProg201.set("anio", 2); mProg201.saveIt();
            Materia mBd201   = new Materia(); mBd201.set("codigo", "BD-201");   mBd201.set("nombre", "Bases de Datos");                  mBd201.set("carga_horaria", 6);   mBd201.set("periodo", "1° Cuatrimestre");  mBd201.set("anio", 2);   mBd201.saveIt();

            Materia mSo201   = new Materia(); mSo201.set("codigo", "SO-201");   mSo201.set("nombre", "Sistemas Operativos");             mSo201.set("carga_horaria", 4);   mSo201.set("periodo", "2° Cuatrimestre");  mSo201.set("anio", 2);   mSo201.saveIt();
            Materia mIs201   = new Materia(); mIs201.set("codigo", "IS-201");   mIs201.set("nombre", "Ingeniería de Software");          mIs201.set("carga_horaria", 6);   mIs201.set("periodo", "2° Cuatrimestre");  mIs201.set("anio", 2);   mIs201.saveIt();

            // 4.5 CORRELATIVAS
            Correlativa corr1 = new Correlativa(); corr1.set("id_materia", mProg102.getId()); corr1.set("id_correlativa", mProg101.getId()); corr1.set("tipo", "R"); corr1.saveIt();
            Correlativa corr2 = new Correlativa(); corr2.set("id_materia", mMat102.getId());  corr2.set("id_correlativa", mMat101.getId());  corr2.set("tipo", "R");  corr2.saveIt();
            Correlativa corr3 = new Correlativa(); corr3.set("id_materia", mProg201.getId()); corr3.set("id_correlativa", mProg102.getId()); corr3.set("tipo", "A"); corr3.saveIt();
            Correlativa corr4 = new Correlativa(); corr4.set("id_materia", mBd201.getId());   corr4.set("id_correlativa", mProg102.getId()); corr4.set("tipo", "R"); corr4.saveIt();
            Correlativa corr5 = new Correlativa(); corr5.set("id_materia", mSo201.getId());   corr5.set("id_correlativa", mProg201.getId()); corr5.set("tipo", "R"); corr5.saveIt();
            Correlativa corr6 = new Correlativa(); corr6.set("id_materia", mIs201.getId());   corr6.set("id_correlativa", mProg201.getId()); corr6.set("tipo", "A"); corr6.saveIt();
            Correlativa corr7 = new Correlativa(); corr7.set("id_materia", mIs201.getId());   corr7.set("id_correlativa", mBd201.getId());   corr7.set("tipo", "R"); corr7.saveIt();

            // 5. COMISIONES
            Comision c1 = new Comision(); c1.set("id_materia", mProg101.getId()); c1.set("horario", "Lunes y Miércoles 18:00 - 20:00"); c1.set("nombre", "Comisión 1"); c1.saveIt();
            Comision c2 = new Comision(); c2.set("id_materia", mMat101.getId());  c2.set("horario", "Martes y Jueves 09:00 - 11:00");   c2.set("nombre", "Comisión 1"); c2.saveIt();
            Comision c3 = new Comision(); c3.set("id_materia", mSo201.getId());   c3.set("horario", "Viernes 14:00 - 18:00");           c3.set("nombre", "Comisión 1"); c3.saveIt();
            Comision c6 = new Comision(); c6.set("id_materia", mProg102.getId()); c6.set("horario", "Lunes y Miércoles 14:00 - 16:00"); c6.set("nombre", "Comisión 1"); c6.saveIt();
            Comision c7 = new Comision(); c7.set("id_materia", mMat102.getId());  c7.set("horario", "Martes y Jueves 16:00 - 18:00");   c7.set("nombre", "Comisión 1"); c7.saveIt();
            Comision c8 = new Comision(); c8.set("id_materia", mProg201.getId()); c8.set("horario", "Lunes 09:00 - 13:00");            c8.set("nombre", "Comisión 1"); c8.saveIt();
            
            // Comisión 2 (Con superposición intencional para pruebas de conflicto en Cronogramas)
            Comision c4 = new Comision(); c4.set("id_materia", mProg101.getId()); c4.set("horario", "Lunes y Miércoles 18:00 - 20:00"); c4.set("nombre", "Comisión 2"); c4.saveIt();
            Comision c5 = new Comision(); c5.set("id_materia", mMat101.getId());  c5.set("horario", "Lunes y Miércoles 19:00 - 21:00"); c5.set("nombre", "Comisión 2"); c5.saveIt();

            // 6. DOCENTE_COMISION (Asignar profesores a las comisiones)
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", pProf1.getId(), c1.getId()); // Turing en PROG-101
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", pProf2.getId(), c2.getId()); // Lovelace en MAT-101
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", pProf1.getId(), c3.getId()); // Turing en SO-201
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", pProf1.getId(), c6.getId()); // Turing en PROG-102
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", pProf2.getId(), c7.getId()); // Lovelace en MAT-102
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", pProf1.getId(), c8.getId()); // Turing en PROG-201

            // 7. INSCRIPCIONES (Inscribir alumnos a las comisiones e Historial Académico)
            // Carlos Pérez (Alumno Nuevo - Ciclo 2026)
            Inscripcion i1 = new Inscripcion(); i1.set("id_alumno", pAlum1.getId()); i1.set("id_materia", mProg101.getId()); i1.set("id_comision", c1.getId()); i1.set("fecha", "2026-03-01 10:00:00"); i1.set("anio_lectivo", 2026); i1.set("cuatrimestre", "1° Cuatrimestre"); i1.saveIt(); // Carlos en PROG-101
            Inscripcion i2 = new Inscripcion(); i2.set("id_alumno", pAlum1.getId()); i2.set("id_materia", mMat101.getId());  i2.set("id_comision", c2.getId()); i2.set("fecha", "2026-03-01 10:30:00"); i2.set("anio_lectivo", 2026); i2.set("cuatrimestre", "1° Cuatrimestre"); i2.saveIt(); // Carlos en MAT-101

            // María Gómez (Alumno Avanzado - Historial Académico Coherente)
            // Año 2024: Primer Cuatrimestre (Habilitante)
            Inscripcion mProg101Past = new Inscripcion(); mProg101Past.set("id_alumno", pAlum2.getId()); mProg101Past.set("id_materia", mProg101.getId()); mProg101Past.set("id_comision", c1.getId()); mProg101Past.set("fecha", "2024-03-01 10:00:00"); mProg101Past.set("anio_lectivo", 2024); mProg101Past.set("cuatrimestre", "1° Cuatrimestre"); mProg101Past.set("condicion", "A"); mProg101Past.saveIt();
            Inscripcion mMat101Past  = new Inscripcion();  mMat101Past.set("id_alumno", pAlum2.getId());  mMat101Past.set("id_materia", mMat101.getId());  mMat101Past.set("id_comision", c2.getId());  mMat101Past.set("fecha", "2024-03-01 10:30:00");  mMat101Past.set("anio_lectivo", 2024);  mMat101Past.set("cuatrimestre", "1° Cuatrimestre");  mMat101Past.set("condicion", "A");  mMat101Past.saveIt();

            // Año 2024: Segundo Cuatrimestre
            Inscripcion mProg102Past = new Inscripcion(); mProg102Past.set("id_alumno", pAlum2.getId()); mProg102Past.set("id_materia", mProg102.getId()); mProg102Past.set("id_comision", c6.getId());        mProg102Past.set("fecha", "2024-08-15 10:00:00"); mProg102Past.set("anio_lectivo", 2024); mProg102Past.set("cuatrimestre", "2° Cuatrimestre"); mProg102Past.set("condicion", "A"); mProg102Past.saveIt();
            Inscripcion mMat102Past  = new Inscripcion();  mMat102Past.set("id_alumno", pAlum2.getId());  mMat102Past.set("id_materia", mMat102.getId());  mMat102Past.set("id_comision", c7.getId());        mMat102Past.set("fecha", "2024-08-15 10:30:00");  mMat102Past.set("anio_lectivo", 2024);  mMat102Past.set("cuatrimestre", "2° Cuatrimestre");  mMat102Past.set("condicion", "A");  mMat102Past.saveIt();

            // Año 2025: Primer Cuatrimestre (Correlativa obligatoria para SO-201)
            Inscripcion mProg201Past = new Inscripcion(); mProg201Past.set("id_alumno", pAlum2.getId()); mProg201Past.set("id_materia", mProg201.getId()); mProg201Past.set("id_comision", c8.getId());        mProg201Past.set("fecha", "2025-03-01 10:00:00"); mProg201Past.set("anio_lectivo", 2025); mProg201Past.set("cuatrimestre", "1° Cuatrimestre"); mProg201Past.set("condicion", "R"); mProg201Past.saveIt();

            // Año 2025: Segundo Cuatrimestre
            Inscripcion i4 = new Inscripcion();           i4.set("id_alumno", pAlum2.getId());           i4.set("id_materia", mSo201.getId());           i4.set("id_comision", c3.getId());           i4.set("fecha", "2025-08-15 14:00:00");           i4.set("anio_lectivo", 2025);           i4.set("cuatrimestre", "2° Cuatrimestre");           i4.set("condicion", "R");           i4.saveIt(); // Maria en SO-201 (Historial pasado)

            // 8. EVALUACIONES (Cargar algunas notas de prueba)
            Evaluacion e1 = new Evaluacion(); e1.set("id_alumno", pAlum1.getId()); e1.set("id_comision", c1.getId()); e1.set("nota", 8); e1.set("fecha", "2026-05-15 19:30:00"); e1.saveIt(); // Carlos en PROG-101
            Evaluacion e2 = new Evaluacion(); e2.set("id_alumno", pAlum2.getId()); e2.set("id_comision", c1.getId()); e2.set("nota", 9); e2.set("fecha", "2024-05-15 19:35:00"); e2.saveIt(); // María en PROG-101 (2024)
            Evaluacion e3 = new Evaluacion(); e3.set("id_alumno", pAlum2.getId()); e3.set("id_comision", c2.getId()); e3.set("nota", 7); e3.set("fecha", "2024-05-15 19:40:00"); e3.saveIt(); // María en MAT-101 (2024)
            Evaluacion e4 = new Evaluacion(); e4.set("id_alumno", pAlum2.getId()); e4.set("id_comision", c6.getId()); e4.set("nota", 8); e4.set("fecha", "2024-11-20 18:00:00"); e4.saveIt(); // María en PROG-102 (2024)
            Evaluacion e5 = new Evaluacion(); e5.set("id_alumno", pAlum2.getId()); e5.set("id_comision", c7.getId()); e5.set("nota", 8); e5.set("fecha", "2024-11-20 18:15:00"); e5.saveIt(); // María en MAT-102 (2024)

            // 9. MENSAJES PÚBLICOS (Foros comunitarios)
            String timestamp = LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Message msg1 = new Message(); msg1.set("id_emisor", uProf1.getId()); msg1.set("id_receptor", null); msg1.set("id_comision", c1.getId()); msg1.set("contenido", "Bienvenidos a la comisión de Programación. Las clases prácticas inician la semana entrante."); msg1.set("fecha", timestamp); msg1.set("leido", false); msg1.saveIt();
            
            Message msg2 = new Message(); msg2.set("id_emisor", uAlum1.getId()); msg2.set("id_receptor", null); msg2.set("id_comision", c1.getId()); msg2.set("contenido", "Profesor, ¿cuál es la bibliografía sugerida para la cursada?"); msg2.set("fecha", LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))); msg2.set("leido", false); msg2.saveIt();

            // 10. NOTIFICACIONES / ALERTAS
            Notification n1 = new Notification(); n1.set("id_usuario", uAlum1.getId()); n1.set("contenido", "Nueva nota cargada: Has recibido un 8 en Introducción a la Programación."); n1.set("fecha", "2026-05-15 19:30:00"); n1.set("leida", false); n1.saveIt();
            Notification n2 = new Notification(); n2.set("id_usuario", uAlum2.getId()); n2.set("contenido", "Nueva nota cargada: Has recibido un 9 en Introducción a la Programación."); n2.set("fecha", "2026-05-15 19:35:00"); n2.set("leida", false); n2.saveIt();

            System.out.println("¡Datos de prueba cargados con éxito!");
        } catch (Exception e) {
            System.err.println("Error ejecutando los seeders: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
