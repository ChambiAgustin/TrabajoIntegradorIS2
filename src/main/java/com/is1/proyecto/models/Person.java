package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;  // Importa la clase base de ActiveJDBC
import org.javalite.activejdbc.annotations.Table;  // Importa la anotacion para la tabla
import org.javalite.activejdbc.annotations.IdName; // Anotacion para indicar el nombre de la FK si no es "ID"

@Table("persons") // Esta anotación asocia explícitamente el modelo 'Person' con la tabla 'persons' en la DB.
@IdName("id_per") // Indica que la clave primaria se llama 'id_per' en lugar de 'id'

public class Person extends Model {
    //ActiveJDBC mapea automáticamente las columnas de la tabla 'persons'
    //(como 'id', 'mail', 'telefono', etc.) a los atributos de esta clase.
    //Y las maneja internamente. No hace falta declararlos aqui de nuevo.
    //Dejar el contructor vacio para ActiveJDBC.
    public String getMail(){
        //Lee el valor de la columna 'mail' de la tabla 'persons'
        return getString("mail");
    }

    public void setMail(String s){
        //Asigna el valor 's' a la columna 'mail'(No la guarda en la Base de Datos)
        set("mail", s);
    }

    public int getDni(){
        //Lee el valor de la columna 'dni' de la tabla 'persons'
        return getInteger("dni");
    }

    public void setDni(int d){
        //Asigna el valor 'd' a la columna 'dni'(No la guarda en la Base de Datos)
        set("dni", d);
    }

    public int getTel(){
        //Lee el valor de la columna 'telefono' de la tabla 'persons'
        return getInteger("tel");
    }

    public void setTel(int t){
        //Asigna el valor 't' a la columna 'telefono'(No la guarda en la Base de Datos)
        set("tel", t);
    }

    public String getNombre(){
        //Lee el valor de la columna 'nombre' de la tabla 'persons'
        return getString("nombre");
    }

    public void setNombre(String n){
        //Asigna el valor 'n' a la columna 'nombre'(No la guarda en la Base de Datos)
        set("nombre", n);
    }

    public String getApellido(){
        //Lee el valor de la columna 'apellido' de la tabla 'persons'
        return getString("apellido");
    }

    public void setApellido(String a){
        //Asigna el valor 'a' a la columna 'apellido'(No la guarda en la Base de Datos)
        set("apellido", a);
    }

    public String getDireccion(){
        //Lee el valor de la columna 'direccion' de la tabla 'persons'
        return getString("direccion");
    }

    public void setDireccion(String dir){
        //Asigna el valor 'dir' a la columna 'direccion'(No la guarda en la Base de Datos)
        set("direccion", dir);
    }

    
}
