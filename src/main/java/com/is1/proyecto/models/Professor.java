package com.is1.proyecto.models;

import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.IdName;

@Table("professors") // Esta anotación asocia explícitamente el modelo 'User' con la tabla 'users' en la DB.
@IdName("id_prof") // Indica que la clave primaria se llama 'id_prof' en lugar de 'id'

public class Professor extends Person {  //Hereda de Person (que ya es un Model)

    // ActiveJDBC mapea automáticamente las columnas de la tabla 'users'
    // (como 'id', 'name', 'password', etc.) a los atributos de esta clase.
    // No necesitas declarar los campos (id, name, password) aquí como variables de instancia,
    // ya que la clase Model base se encarga de la interacción con la base de datos.

    // Opcional: Puedes agregar métodos getters y setters si prefieres un acceso más tipado,
    // aunque los métodos genéricos de Model (getString(), set(), getInteger(), etc.) ya funcionan.

    //Estos metodos ahora leeran y escribiran en las columnas 'legajo' y 'cargo' de la tabla 'professors'.

    public void setLegajo(int s){
        set("legajo", s); // Asigna el valor 's' para la columna 'legajo'
    }

    public void setCargo(String s){
        set("cargo", s); // Asigna el valor 's' para la columna 'cargo'
    }

    public int getLegajo(){
        return getInteger("legajo"); // Obtiene el valor de la columna 'legajo'
    }

    public String getCargo(){
        return getString("cargo"); // Obtiene el valor de la columna 'cargo'
    }

}
