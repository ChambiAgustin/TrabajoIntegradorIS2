# Documentación del Práctico 2B

## Integrantes del Equipo
* Virginia Gamba
* Mercedes Vega
* Genaro Alvares
* Chambi Agustin Ezequiel  
* Santiago Denaro

# Analisis de Patrones de Diseño

En el codigo base provisto, hemos identificado la implementación del patrón de diseño *Singleton*.

# 1. ¿Qué patron identificaron?
El patrón identificado es el *Singleton*. Pertenece a la categoría de patrones *Creacionales*, cuyo objetivo es garantizar que una clase tenga una única instancia y proporcionar un punto de acceso global a ella.

# 2. ¿Dónde y como se aplica en el codigo?
El patrón se encuentra implementado en la clase:
`src/main/java/com/is1/proyecto/config/DBConfigSingleton.java`

Se aplica utilizando:
* *Constructor Privado:* `private DBConfigSingleton() { ... }` evita que otras clases puedan instanciarlo directamente con `new`.
* *Instancia Estática:* `private static DBConfigSingleton instance;` mantiene la referencia única.
* *Método de Acceso Estático:* `public static synchronized DBConfigSingleton getInstance()` controla el acceso, creando la instancia solo si no existe (Lazy Initialization) y devolviendo siempre la misma.

# 3. ¿Qué problema resuelve este patrón en ese contexto?
En el contexto de nuestra aplicación web, necesitamos conectarnos a la base de datos SQLite desde múltiples lugares (al iniciar la app, en cada request, etc.).
El Singleton resuelve el problema de la gestión de la configuración de la base de datos (URL, driver, credenciales). Evita tener múltiples objetos de configuración dispersos por el sistema, centralizando los datos de conexión en un único lugar y asegurando que todos los componentes utilicen la misma configuración de manera consistente y eficiente.