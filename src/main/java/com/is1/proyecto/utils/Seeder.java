package com.is1.proyecto.utils;

import com.is1.proyecto.models.Person;
import com.is1.proyecto.models.Professor;
import com.is1.proyecto.models.User;
import org.mindrot.jbcrypt.BCrypt;

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
            pAdmin.setNombre("Admin"); pAdmin.setApellido("Sistema"); pAdmin.setMail("admin@puntodigital.gov.ar"); pAdmin.setDni(11111111); pAdmin.saveIt();
            User uAdmin = new User();
            uAdmin.setName("admin"); uAdmin.setPassword(BCrypt.hashpw("admin123", BCrypt.gensalt())); uAdmin.setTipoUsuario("ADMINISTRADOR"); uAdmin.setPersonId((Integer) pAdmin.getId()); uAdmin.saveIt();

            // 2. PROFESORES
            Person pProf1 = new Person();
            pProf1.setNombre("Alan"); pProf1.setApellido("Turing"); pProf1.setMail("aturing@puntodigital.gov.ar"); pProf1.setDni(22222222); pProf1.saveIt();
            Professor prof1 = new Professor(); prof1.setId(pProf1.getId()); prof1.setLegajo(22222222); prof1.setCargo("Titular"); prof1.saveIt();
            User uProf1 = new User();
            uProf1.setName("22222222"); uProf1.setPassword(BCrypt.hashpw("22222222", BCrypt.gensalt())); uProf1.setTipoUsuario("DOCENTE"); uProf1.setPersonId((Integer) pProf1.getId()); uProf1.saveIt();

            Person pProf2 = new Person();
            pProf2.setNombre("Ada"); pProf2.setApellido("Lovelace"); pProf2.setMail("alovelace@puntodigital.gov.ar"); pProf2.setDni(33333333); pProf2.saveIt();
            Professor prof2 = new Professor(); prof2.setId(pProf2.getId()); prof2.setLegajo(33333333); prof2.setCargo("JTP"); prof2.saveIt();
            User uProf2 = new User();
            uProf2.setName("33333333"); uProf2.setPassword(BCrypt.hashpw("33333333", BCrypt.gensalt())); uProf2.setTipoUsuario("DOCENTE"); uProf2.setPersonId((Integer) pProf2.getId()); uProf2.saveIt();

            // 3. ALUMNOS
            Person pAlum1 = new Person();
            pAlum1.setNombre("Carlos"); pAlum1.setApellido("Perez"); pAlum1.setMail("cperez@alumno.com"); pAlum1.setDni(44444444); pAlum1.saveIt();
            User uAlum1 = new User();
            uAlum1.setName("44444444"); uAlum1.setPassword(BCrypt.hashpw("44444444", BCrypt.gensalt())); uAlum1.setTipoUsuario("ALUMNO"); uAlum1.setPersonId((Integer) pAlum1.getId()); uAlum1.saveIt();

            Person pAlum2 = new Person();
            pAlum2.setNombre("Maria"); pAlum2.setApellido("Gomez"); pAlum2.setMail("mgomez@alumno.com"); pAlum2.setDni(55555555); pAlum2.saveIt();
            User uAlum2 = new User();
            uAlum2.setName("55555555"); uAlum2.setPassword(BCrypt.hashpw("55555555", BCrypt.gensalt())); uAlum2.setTipoUsuario("ALUMNO"); uAlum2.setPersonId((Integer) pAlum2.getId()); uAlum2.saveIt();

            System.out.println("¡Datos de prueba cargados con éxito!");
        } catch (Exception e) {
            System.err.println("Error ejecutando los seeders: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
