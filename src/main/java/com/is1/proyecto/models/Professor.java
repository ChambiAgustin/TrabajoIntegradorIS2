package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;  // <<< ¡DEBE IMPORTAR Y EXTENDER "Model"!
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.IdName;

@Table("professors") // Esta anotación asocia el modelo con la tabla 'professors'
@IdName("id_prof") // Indica que la clave primaria se llama 'id_prof'

// ¡ESTA ES LA LÍNEA MÁS IMPORTANTE DEL ARREGLO!
public class Professor extends Model {  // <<< ¡NO DEBE EXTENDER "Person"!

    // ActiveJDBC mapea automáticamente las columnas.
    // Dejamos los getters y setters que tenías para legajo y cargo.

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