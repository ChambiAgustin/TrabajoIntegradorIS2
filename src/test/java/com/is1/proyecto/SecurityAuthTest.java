package com.is1.proyecto;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.javalite.activejdbc.Base;
import org.mindrot.jbcrypt.BCrypt;
import com.is1.proyecto.models.User;
import com.is1.proyecto.models.Person;
import spark.Spark;

/**
 * Suite de pruebas de seguridad automatizada (QA) para evaluar la resistencia
 * de la aplicación ante vulnerabilidades de "Broken Authentication" del OWASP Top 10.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class SecurityAuthTest {

    private static final String BASE_URL = "http://localhost:8085";
    private static final String TEST_DB_PATH = "target/security-test.db";
    private static final String TEST_DB_URL = "jdbc:sqlite:" + TEST_DB_PATH;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @BeforeAll
    static void setUpAll() throws Exception {
        // 1. Limpieza e inicialización de la base de datos temporal SQLite
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        dbFile.getParentFile().mkdirs();

        // Leer scheme.sql y separar sentencias para ejecución JDBC directa
        String schemaSql = Files.readString(Paths.get("src/main/resources/scheme.sql"), StandardCharsets.UTF_8);
        String[] statements = schemaSql.split(";");

        try (Connection conn = DriverManager.getConnection(TEST_DB_URL);
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }

        // 2. Establecer db.url del sistema para que DBConfigSingleton la consuma
        System.setProperty("db.url", TEST_DB_URL);
        System.setProperty("server.port", "8085");

        // 3. Levantar la aplicación Spark
        App.main(new String[0]);
        Spark.awaitInitialization();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        // Detener Spark y limpiar recursos
        Spark.stop();
        Spark.awaitStop();
        System.clearProperty("db.url");
        System.clearProperty("server.port");
        File dbFile = new File(TEST_DB_PATH);

        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @BeforeEach
    void setUp() {
        // Limpieza de tablas antes de cada test para asegurar aislamiento absoluto
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Base.exec("DELETE FROM mensajes");
            Base.exec("DELETE FROM professors");
            Base.exec("DELETE FROM alumnos");
            Base.exec("DELETE FROM users");
            Base.exec("DELETE FROM persons");
        } finally {
            Base.close();
        }
    }

    @AfterEach
    void tearDown() {
        // Asegurar cierre de conexiones residuales en el hilo del test
        if (Base.hasConnection()) {
            Base.close();
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private HttpRequest.BodyPublisher ofForm(Map<String, String> data) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

    private void registerUser(String username, String password) throws Exception {
        Map<String, String> params = Map.of(
            "name", username,
            "password", password,
            "tipo_usuario", "ALUMNO"
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/user/new"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(params))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> loginAttempt(String username, String password) throws Exception {
        Map<String, String> params = Map.of(
            "username", username,
            "password", password
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(params))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String extractSessionId(HttpResponse<?> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        for (String cookie : cookies) {
            if (cookie.toUpperCase().startsWith("JSESSIONID=")) {
                int endIndex = cookie.indexOf(';');
                if (endIndex == -1) {
                    return cookie;
                }
                return cookie.substring(0, endIndex);
            }
        }
        return null;
    }

    // --- CASOS DE PRUEBA OWASP TOP 10 ---

    /**
     * 1. Política de contraseñas: el registro rechaza contraseñas cortas/débiles
     * ("12345", "password") y exige longitud mínima y complejidad.
     */
    @Test
    void test01_PasswordPolicy_WeakPasswordRejected() throws Exception {
        // Test Positivo: Contraseña fuerte debe registrarse con éxito
        String strongUser = "strong_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> strongParams = Map.of(
            "name", strongUser,
            "password", "P@ssw0rd_Strong123!",
            "tipo_usuario", "ALUMNO"
        );
        HttpRequest requestStrong = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/user/new"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(strongParams))
                .build();
        HttpResponse<String> responseStrong = client.send(requestStrong, HttpResponse.BodyHandlers.ofString());
        assertTrue(responseStrong.statusCode() == 201 || responseStrong.statusCode() == 302,
                "Se esperaba éxito (201/302) al registrar con contraseña fuerte.");

        // Test Negativo: Contraseña corta/débil debe ser rechazada con 400 Bad Request
        String weakUser = "weak_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> weakParams = Map.of(
            "name", weakUser,
            "password", "12345",
            "tipo_usuario", "ALUMNO"
        );
        HttpRequest requestWeak = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/user/new"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(weakParams))
                .build();
        HttpResponse<String> responseWeak = client.send(requestWeak, HttpResponse.BodyHandlers.ofString());

        // Aserción de Seguridad: La contraseña débil debe rechazarse
        assertEquals(400, responseWeak.statusCode(),
                "VULNERABILIDAD: La aplicación aceptó una contraseña débil y corta ('12345') sin validaciones.");
    }

    /**
     * 2. Hashing: la contraseña NO está en texto plano ni MD5/SHA1 en base de datos.
     * Debe estar hasheada con bcrypt/scrypt/Argon2 + salt.
     */
    @Test
    void test02_PasswordHashing_VerifyOnlyHashesInDb() throws Exception {
        // 1. Test Positivo: Registrar un usuario legítimo via /user/new
        String secureUser = "securehash_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(secureUser, "MyStrongPassword123!");

        // 2. Sembrar un usuario con contraseña plana y otro con MD5 en la DB de test para auditar el scanner de base de datos
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Base.exec("INSERT INTO users (name, password, tipo_usuario) VALUES (?, ?, ?)", 
                    "plain_user", "PlaintextPassword123", "ALUMNO");
            Base.exec("INSERT INTO users (name, password, tipo_usuario) VALUES (?, ?, ?)", 
                    "md5_user", "827ccb0eea8a706c4c34a16891f84e7b", "ALUMNO"); // MD5 de '12345'
        } finally {
            Base.close();
        }

        // 3. Auditar todos los usuarios de la base de datos de test
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            List<User> allUsers = User.findAll();
            assertFalse(allUsers.isEmpty());
            for (User u : allUsers) {
                String storedPassword = u.getString("password");
                // Verificar formato bcrypt ($2a$, $2b$ o $2y$)
                assertTrue(storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$"),
                        "VULNERABILIDAD CRÍTICA: Se encontró la contraseña del usuario '" + u.getString("name") 
                        + "' en formato inseguro (texto plano o cifrado débil). Valor: " + storedPassword);
            }
        } finally {
            Base.close();
        }
    }

    /**
     * 3. Fuerza bruta: tras N intentos fallidos hay bloqueo o rate limiting,
     * y el bloqueo impide nuevos intentos.
     */
    @Test
    void test03_BruteForce_LockoutOrRateLimitAfterNFailedAttempts() throws Exception {
        String bfUser = "bfuser_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(bfUser, "CorrectPassword123!");

        // 1. Simular 5 intentos fallidos consecutivos
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> failRes = loginAttempt(bfUser, "WrongPassword_" + i);
            assertEquals(401, failRes.statusCode(), "Se esperaba 401 en login fallido.");
        }

        // 2. 6º intento con contraseña correcta debe bloquearse
        HttpResponse<String> successRes = loginAttempt(bfUser, "CorrectPassword123!");

        // Aserción de Seguridad: Cuenta bloqueada (429, 423 o 403)
        assertTrue(successRes.statusCode() == 429 || successRes.statusCode() == 423 || successRes.statusCode() == 403,
                "VULNERABILIDAD: No hay bloqueo de cuenta o rate-limiting. El login fue exitoso tras múltiples fallos.");
    }

    /**
     * 4. Enumeración de usuarios: la vista de error muestra un mensaje genérico
     * idéntico tanto si el usuario no existe como si la contraseña es incorrecta.
     */
    @Test
    void test04_UserEnumeration_IdenticalErrorMessages() throws Exception {
        String existingUser = "enumuser_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(existingUser, "Password123!");

        // Caso A: Usuario no existente
        HttpResponse<String> resNonExistent = loginAttempt("nonexistent_" + UUID.randomUUID(), "somepass");
        
        // Caso B: Usuario existente, contraseña incorrecta
        HttpResponse<String> resWrongPassword = loginAttempt(existingUser, "wrongpass");

        assertEquals(401, resNonExistent.statusCode());
        assertEquals(401, resWrongPassword.statusCode());

        // Asegurar que las respuestas HTML sean idénticas
        String bodyA = resNonExistent.body();
        String bodyB = resWrongPassword.body();
        assertEquals(bodyA, bodyB, "VULNERABILIDAD: Los mensajes/plantillas de error permiten la enumeración de usuarios.");
    }

    /**
     * 5. Sesiones de Jetty: cookie con HttpOnly, Secure y SameSite. Invalidación
     * al logout y timeout configurado.
     */
    @Test
    void test05_JettySessions_ValidateCookieAttributesAndTimeout() throws Exception {
        String user = "sessuser_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(user, "Password123!");
        HttpResponse<String> loginRes = loginAttempt(user, "Password123!");

        List<String> setCookies = loginRes.headers().allValues("Set-Cookie");
        assertFalse(setCookies.isEmpty(), "No se emitió cookie de sesión tras login.");

        String cookieHeader = setCookies.get(0);

        // Aserciones de Atributos de Cookie de Seguridad
        assertTrue(cookieHeader.toLowerCase().contains("httponly"),
                "VULNERABILIDAD: Cookie de sesión carece del atributo HttpOnly.");
        assertTrue(cookieHeader.toLowerCase().contains("secure"),
                "VULNERABILIDAD: Cookie de sesión carece del atributo Secure.");
        assertTrue(cookieHeader.toLowerCase().contains("samesite"),
                "VULNERABILIDAD: Cookie de sesión carece del atributo SameSite.");

        // Invalidación al Logout
        String sessionId = extractSessionId(loginRes);
        assertNotNull(sessionId);

        HttpRequest logoutReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/logout"))
                .header("Cookie", sessionId)
                .GET()
                .build();
        HttpResponse<String> logoutRes = client.send(logoutReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, logoutRes.statusCode(), "Logout debe redirigir.");

        // Intentar acceder a zona protegida (dashboard) con la sesión destruida
        HttpRequest dashboardReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/dashboard"))
                .header("Cookie", sessionId)
                .GET()
                .build();
        HttpResponse<String> dashboardRes = client.send(dashboardReq, HttpResponse.BodyHandlers.ofString());

        // Debe redirigir al login al no ser válida la sesión
        assertEquals(302, dashboardRes.statusCode(),
                "VULNERABILIDAD: La sesión no se invalidó en logout; sigue permitiendo acceso.");
    }

    /**
     * 6. Fijación de sesión: capturá el ID de sesión antes y después del login
     * y confirmá que se regenera tras autenticarse correctamente.
     */
    @Test
    void test06_SessionFixation_SessionIdRegeneratedOnLogin() throws Exception {
        // 1. Obtener sesión anónima accediendo a /dashboard
        HttpRequest anonReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/dashboard"))
                .GET()
                .build();
        HttpResponse<String> anonRes = client.send(anonReq, HttpResponse.BodyHandlers.ofString());
        
        String anonCookie = extractSessionId(anonRes);
        assertNotNull(anonCookie, "El servidor no asignó una cookie de sesión inicial al visitante anónimo.");

        // 2. Registrar y autenticarse pasando la cookie anónima
        String user = "fixuser_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(user, "Password123!");

        Map<String, String> loginParams = Map.of(
            "username", user,
            "password", "Password123!"
        );
        HttpRequest loginReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", anonCookie)
                .POST(ofForm(loginParams))
                .build();
        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());

        String postLoginCookie = extractSessionId(loginRes);

        // Aserciones de Fijación de Sesión
        if (postLoginCookie == null) {
            fail("VULNERABILIDAD DE FIJACIÓN DE SESIÓN: El servidor reutilizó implícitamente el ID anónimo (" 
                    + anonCookie + ") al no retornar cabecera Set-Cookie tras autenticación.");
        } else if (postLoginCookie.equals(anonCookie)) {
            fail("VULNERABILIDAD DE FIJACIÓN DE SESIÓN: El ID de sesión tras el login es idéntico al ID anónimo (" 
                    + anonCookie + ").");
        } else {
            assertNotEquals(anonCookie, postLoginCookie, "El ID de sesión debió cambiar.");
        }
    }

    /**
     * 7. Transporte seguro: los endpoints de auth no aceptan credenciales sobre HTTP plano.
     */
    @Test
    void test07_SecureTransport_RejectHttpOnAuthEndpoints() throws Exception {
        Map<String, String> params = Map.of(
            "username", "testuser",
            "password", "testpass"
        );
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(params))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        // Aserción de Seguridad: Tráfico HTTP plano debe ser denegado (403) o redirigido (301/308)
        assertTrue(res.statusCode() == 403 || res.statusCode() == 301 || res.statusCode() == 308,
                "VULNERABILIDAD: Los endpoints de auth aceptan peticiones sobre HTTP plano sin cifrar.");
    }

    /**
     * 8. Recuperación de contraseña: el token es de un solo uso, expira y no es
     * adivinable; el flujo no filtra información sensible.
     */
    @Test
    void test08_PasswordRecovery_VerifySecureTokenFlow() throws Exception {
        // Validar si existe la funcionalidad y endpoints seguros (/recover o similar)
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/recover"))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

        // Aserción de Seguridad: El endpoint debe estar implementado y protegido
        assertNotEquals(404, res.statusCode(),
                "VULNERABILIDAD / CARENCIA: No existe funcionalidad o endpoint de recuperación de contraseñas.");
    }

    /**
     * 9. Fuga de datos en las vistas Mustache: ninguna plantilla renderiza stack
     * traces, hashes de contraseña ni datos internos ante un error.
     */
    @Test
    void test09_DataLeakage_ErrorViewsDoNotLeakInternalDetails() throws Exception {
        // Forzar error de base de datos llamando a /add_users (falla por NOT NULL en tipo_usuario)
        String name = "leak_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> params = Map.of(
            "name", name,
            "password", "SomePass123"
        );
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/add_users"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(params))
                .build();
        
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        String body = res.body();

        // Aserción de Seguridad: El cuerpo no debe contener fragmentos técnicos que revelen el motor de DB o excepciones internas
        assertFalse(body.toLowerCase().contains("sqlite") ||
                    body.toLowerCase().contains("constraint") ||
                    body.toLowerCase().contains("users") ||
                    body.toLowerCase().contains("table"),
                "VULNERABILIDAD: La respuesta expone detalles internos de la base de datos (Data Leakage): " + body);
    }

    /**
     * 10. Validación de JWT (No implementado en esta aplicación al usar sesiones Jetty).
     */
    @Test
    void test10_JWT_SkippedAsNotImplemented() {
        System.out.println("[INFO] Test 10: JWT no se encuentra implementado en la aplicación. Suite omitida.");
        assertTrue(true, "JWT no aplicable.");
    }

    /**
     * 11. Flujo de Cambio Obligatorio de Contraseña:
     * - Intenta loguearse con la clave temporal de docente.
     * - Valida que se le redirija a /password/change.
     * - Valida que no pueda acceder a /dashboard estando en el estado mustChange.
     * - Realiza un cambio exitoso y valida que entonces sí pueda navegar a /dashboard.
     */
    @Test
    void test11_ForcedPasswordChangeFlow() throws Exception {
        // 1. Insertar un usuario docente con must_change_password = 1 en la base de datos
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      999, 99999999, "temp_docente@test.com", 123456, "Temp", "Docente", "Calle 123");
            Base.exec("INSERT INTO professors (id_prof, legajo, cargo) VALUES (?, ?, ?)",
                      999, 99999999, "Docente");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, must_change_password, temp_password_created_at) VALUES (?, ?, ?, ?, ?, ?)",
                      "99999999", BCrypt.hashpw("TempPass123!", BCrypt.gensalt()), "DOCENTE", 999, 1, 
                      java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } finally {
            Base.close();
        }

        // 2. Intentar autenticarse con la contraseña temporal
        HttpResponse<String> loginRes = loginAttempt("99999999", "TempPass123!");
        assertEquals(302, loginRes.statusCode());
        assertTrue(loginRes.headers().firstValue("Location").orElse("").contains("/password/change"),
                "El login exitoso con clave temporal debió redirigir a /password/change");

        String sessionCookie = extractSessionId(loginRes);
        assertNotNull(sessionCookie, "No se emitió cookie de sesión tras login con temporal.");

        // 3. Intentar navegar a /dashboard estando forzado al cambio
        HttpRequest dashboardReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/dashboard"))
                .header("Cookie", sessionCookie)
                .GET()
                .build();
        HttpResponse<String> dashboardRes = client.send(dashboardReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, dashboardRes.statusCode());
        assertTrue(dashboardRes.headers().firstValue("Location").orElse("").contains("/password/change"),
                "El filtro de seguridad debió bloquear el acceso al dashboard y redirigir a /password/change");

        // 4. Intentar realizar el cambio de contraseña
        // Caso A: Contraseña débil o no coincide
        Map<String, String> weakChangeParams = Map.of(
            "newPassword", "123",
            "confirmPassword", "123"
        );
        HttpRequest weakChangeReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/password/change"))
                .header("Cookie", sessionCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(weakChangeParams))
                .build();
        HttpResponse<String> weakChangeRes = client.send(weakChangeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, weakChangeRes.statusCode()); // Redirige de vuelta a /password/change con errores en sesión

        // Caso B: Contraseña válida
        Map<String, String> validChangeParams = Map.of(
            "newPassword", "NuevaContrasenaFuerte123!",
            "confirmPassword", "NuevaContrasenaFuerte123!"
        );
        HttpRequest validChangeReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/password/change"))
                .header("Cookie", sessionCookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(validChangeParams))
                .build();
        HttpResponse<String> validChangeRes = client.send(validChangeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, validChangeRes.statusCode());
        assertTrue(validChangeRes.headers().firstValue("Location").orElse("").contains("/dashboard"),
                "El cambio exitoso debió redirigir a /dashboard");

        // 5. Verificar que ahora sí pueda acceder al /dashboard
        HttpResponse<String> successDashboardRes = client.send(dashboardReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, successDashboardRes.statusCode(), "El docente debió ingresar al dashboard con éxito.");
    }

    /**
     * 12. Test de Panel de Administración y Aprobación:
     * - Inserta un administrador y un usuario en estado "pendiente".
     * - Inicia sesión como administrador y accede al /dashboard.
     * - Verifica que en el HTML del /dashboard se incluya el contador de pendientes (ej. "pendiente(s)")
     * - Accede a /admin/pendientes y realiza la aprobación del usuario.
     * - Valida que el usuario quede en estado "activo".
     */
    @Test
    void test12_AdminPanelAndUserApproval() throws Exception {
        // 1. Insertar admin y usuario pendiente en la base de datos
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            // Admin
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      1000, 11111111, "admin@test.com", 111111, "Admin", "User", "Admin Office");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "11111111", BCrypt.hashpw("Admin123!", BCrypt.gensalt()), "ADMIN", 1000, "activo", 1);

            // Pendiente
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      1001, 22222222, "alumno@test.com", 222222, "Alumno", "Pendiente", "Calle Alumno");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo, ingresante) VALUES (?, ?, ?)",
                      1001, 22222222, 1);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "22222222", BCrypt.hashpw("Student123!", BCrypt.gensalt()), "ALUMNO", 1001, "pendiente", 0);
        } finally {
            Base.close();
        }

        // 2. Iniciar sesión como Admin
        HttpResponse<String> loginRes = loginAttempt("11111111", "Admin123!");
        assertEquals(302, loginRes.statusCode());
        String adminSessionCookie = extractSessionId(loginRes);
        assertNotNull(adminSessionCookie);

        // 3. Acceder al dashboard de Admin y verificar que contiene el badge de pendientes
        HttpRequest dashboardReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/dashboard"))
                .header("Cookie", adminSessionCookie)
                .GET()
                .build();
        HttpResponse<String> dashboardRes = client.send(dashboardReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, dashboardRes.statusCode());
        String dashboardBody = dashboardRes.body();
        assertTrue(dashboardBody.contains("Aprobar Solicitudes") && dashboardBody.contains("pendiente(s)"),
                "El dashboard del administrador debe mostrar el botón de Aprobar Solicitudes y el badge de pendiente(s).");

        // 4. Acceder a /admin/pendientes y verificar que muestra al alumno pendiente
        HttpRequest pendientesReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/admin/pendientes"))
                .header("Cookie", adminSessionCookie)
                .GET()
                .build();
        HttpResponse<String> pendientesRes = client.send(pendientesReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pendientesRes.statusCode());
        String pendientesBody = pendientesRes.body();
        assertTrue(pendientesBody.contains("Alumno Pendiente") && pendientesBody.contains("alumno@test.com"),
                "El listado de pendientes debe mostrar la solicitud del alumno.");

        // 5. Obtener el ID del usuario pendiente para aprobarlo.
        int alumnoUserId;
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            alumnoUserId = ((Number) User.findFirst("name = ?", "22222222").getId()).intValue();
        } finally {
            Base.close();
        }

        // 6. Enviar POST para aprobar al usuario
        HttpRequest approveReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/admin/aprobar/" + alumnoUserId))
                .header("Cookie", adminSessionCookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> approveRes = client.send(approveReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, approveRes.statusCode()); // Debe redirigir de vuelta a pendientes

        // 7. Verificar en base de datos que el usuario ahora está "activo" y "active = 1"
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            User u = User.findFirst("name = ?", "22222222");
            assertEquals("activo", u.getString("estado"));
            assertEquals(1, u.getInteger("active"));
        } finally {
            Base.close();
        }
    }

    /**
     * 13. Test de Clasificación y Secciones Desplegables de Usuarios:
     * - Inserta un administrador, un docente y dos alumnos con distintas fechas de registro.
     * - Inicia sesión como administrador y accede a /admin/usuarios.
     * - Valida que la respuesta HTML contenga los grupos de docentes y alumnos.
     * - Valida que contenga el agrupamiento por fecha para alumnos.
     * - Valida que contenga el script JS de control para plegar/desplegar secciones.
     */
    @Test
    void test13_AdminUsersGroupingAndCollapsibleTabs() throws Exception {
        // 1. Insertar usuarios de prueba en la base de datos
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            // Admin
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      2000, 31111111, "admin2@test.com", 111111, "Admin", "Dos", "Admin Office");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "31111111", BCrypt.hashpw("Admin123!", BCrypt.gensalt()), "ADMIN", 2000, "activo", 1);

            // Docente
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      2001, 32222222, "docente2@test.com", 222222, "Docente", "Prueba", "Calle Docente");
            Base.exec("INSERT INTO professors (id_prof, legajo, cargo) VALUES (?, ?, ?)",
                      2001, 32222222, "Titular");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      "32222222", BCrypt.hashpw("Teacher123!", BCrypt.gensalt()), "DOCENTE", 2001, "activo", 1, "2026-06-07 09:00:00");

            // Alumno 1 (Fecha: 2026-06-07)
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      2002, 33333333, "alumno1@test.com", 333333, "Alumno", "Uno", "Calle Uno");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo, ingresante) VALUES (?, ?, ?)",
                      2002, 33333333, 1);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      "33333333", BCrypt.hashpw("Student123!", BCrypt.gensalt()), "ALUMNO", 2002, "activo", 1, "2026-06-07 10:30:00");

            // Alumno 2 (Fecha: 2026-06-06)
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      2003, 34444444, "alumno2@test.com", 444444, "Alumno", "Dos", "Calle Dos");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo, ingresante) VALUES (?, ?, ?)",
                      2003, 34444444, 1);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      "34444444", BCrypt.hashpw("Student123!", BCrypt.gensalt()), "ALUMNO", 2003, "activo", 1, "2026-06-06 14:00:00");
        } finally {
            Base.close();
        }

        // 2. Iniciar sesión como Admin
        HttpResponse<String> loginRes = loginAttempt("31111111", "Admin123!");
        assertEquals(302, loginRes.statusCode());
        String adminSessionCookie = extractSessionId(loginRes);
        assertNotNull(adminSessionCookie);

        // 3. Solicitar /admin/usuarios
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/admin/usuarios"))
                .header("Cookie", adminSessionCookie)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());

        String body = res.body();
        // Aserciones sobre estructura y visualización
        assertTrue(body.contains("Docentes (1)"), "Debe mostrar el encabezado de docentes con su contador.");
        assertTrue(body.contains("Alumnos (2)"), "Debe mostrar el encabezado de alumnos con su contador.");
        assertTrue(body.contains("Año de Registro: <strong class=\"text-indigo-600\">2026</strong>"), "Debe clasificar a los alumnos en el año 2026.");
        assertTrue(body.contains("2026-06-07"), "Debe incluir la fecha 2026-06-07 en la tabla.");
        assertTrue(body.contains("2026-06-06"), "Debe incluir la fecha 2026-06-06 en la tabla.");
        assertFalse(body.contains("id=\"date-section-"), "No debe haber secciones replegables para las fechas.");
        assertTrue(body.contains("Docente Prueba"), "Debe incluir el nombre del docente en el listado.");
        assertTrue(body.contains("Alumno Uno"), "Debe incluir al alumno Uno.");
        assertTrue(body.contains("Alumno Dos"), "Debe incluir al alumno Dos.");
        assertTrue(body.contains("function toggleSection"), "Debe inyectar el script de JavaScript para manejar el plegado de secciones.");
        assertTrue(body.contains("id=\"section-docentes\" class=\"hidden"), "La sección de docentes debe estar replegada (hidden) por defecto.");
        assertTrue(body.contains("id=\"section-alumnos\" class=\"hidden"), "La sección de alumnos debe estar replegada (hidden) por defecto.");
        assertTrue(body.contains("id=\"anio-section-2026\" class=\"hidden"), "La sección del año 2026 debe estar replegada (hidden) por defecto.");
    }

    /**
     * 14. Test de Superposición de Docentes Inter-Anual:
     * - Inserta un administrador, un docente, y dos materias en diferentes años (1° y 2° Año) en el mismo periodo.
     * - Crea una comisión para cada materia con el mismo horario y asigna el docente a ambas.
     * - Inicia sesión como administrador y solicita el cronograma del 1° Año.
     * - Valida que se informe la superposición de docentes en el cronograma, mostrando ambas materias y sus años.
     */
    @Test
    void test14_TeacherConflictInterAnual() throws Exception {
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            // Admin
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      3000, 41111111, "admin3@test.com", 111111, "Admin", "Tres", "Admin Office");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "41111111", BCrypt.hashpw("Admin123!", BCrypt.gensalt()), "ADMIN", 3000, "activo", 1);

            // Docente
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      3001, 42222222, "teacher3@test.com", 222222, "Docente", "Conflicto", "Calle Docente");
            Base.exec("INSERT INTO professors (id_prof, legajo, cargo) VALUES (?, ?, ?)",
                      3001, 42222222, "Adjunto");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "42222222", BCrypt.hashpw("Teacher123!", BCrypt.gensalt()), "DOCENTE", 3001, "activo", 1);

            // Materias (1° Año y 2° Año en 1° Cuatrimestre)
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      301, "MAT-301", "Materia Uno", 4, "1° Cuatrimestre", 1);
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      302, "MAT-302", "Materia Dos", 4, "1° Cuatrimestre", 2);

            // Comisiones con idéntico horario
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      311, 301, "Viernes 08:00 - 10:00", "Comisión 1");
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      312, 302, "Viernes 08:00 - 10:00", "Comisión 1");

            // Asignación de docente a ambas comisiones
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", 3001, 311);
            Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", 3001, 312);
        } finally {
            Base.close();
        }

        // 1. Iniciar sesión como Admin
        HttpResponse<String> loginRes = loginAttempt("41111111", "Admin123!");
        assertEquals(302, loginRes.statusCode());
        String adminSessionCookie = extractSessionId(loginRes);
        assertNotNull(adminSessionCookie);

        // 2. Solicitar cronograma de 1er Año, 1° Cuatrimestre
        String encodedPeriod = URLEncoder.encode("1° Cuatrimestre", StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/planificacion/cronograma/1/" + encodedPeriod))
                .header("Cookie", adminSessionCookie)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());

        String body = res.body();
        // Aserciones de conflicto e información en pantalla
        assertTrue(body.contains("Superposición Detectada"), "Debe mostrar el indicador general de conflicto.");
        assertTrue(body.contains("⚠️ Superposición Docente"), "Debe mostrar la etiqueta de advertencia de docente en la celda.");
        assertTrue(body.contains("MAT-301"), "Debe listar la materia MAT-301 en el conflicto.");
        assertTrue(body.contains("MAT-302"), "Debe listar la materia MAT-302 en el conflicto.");
        assertTrue(body.contains("1° Año"), "Debe mostrar el badge del año para MAT-301.");
        assertTrue(body.contains("2° Año"), "Debe mostrar el badge del año para la materia en conflicto MAT-302.");

        // Validar que se incluyó MAT-301 en el desplegable (ya que es del 1er año)
        assertTrue(body.contains("MAT-301"), "El desplegable debe incluir MAT-301.");
        // Validar que dado que MAT-301 ya está en el cronograma para Comisión 1, figura con data-added="true"
        assertTrue(body.contains("value=\"301\" data-added=\"true\""), "MAT-301 debe marcarse como añadida.");
    }

    @Test
    void test15_AutomaticAndManualCommissionAssignment() throws Exception {
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            // Habilitar periodo de inscripción
            Base.exec("DELETE FROM configuraciones");
            Base.exec("INSERT INTO configuraciones (clave, valor) VALUES ('inscripcion_activa', 'true')");
            Base.exec("INSERT INTO configuraciones (clave, valor) VALUES ('inscripcion_periodo', '1° Cuatrimestre')");
            Base.exec("INSERT INTO configuraciones (clave, valor) VALUES ('inscripcion_anio', '2026')");

            // Materias
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      401, "MAT-401", "Materia Cuatro Cero Uno", 4, "1° Cuatrimestre", 1);
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      402, "MAT-402", "Materia Cuatro Cero Dos", 4, "1° Cuatrimestre", 1);

            // Comisiones para 401: Comisión 1 y Comisión 2
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      411, 401, "Viernes 08:00 - 10:00", "Comisión 1");
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      412, 401, "Viernes 10:00 - 12:00", "Comisión 2");

            // Comisiones para 402: Comisión 1 y Comisión 2
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      421, 402, "Lunes 08:00 - 10:00", "Comisión 1");
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      422, 402, "Lunes 10:00 - 12:00", "Comisión 2");

            // Registrar Administrador
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      3000, 41111111, "admin3@test.com", 111111, "Admin", "Tres", "Admin Office");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "41111111", BCrypt.hashpw("Admin123!", BCrypt.gensalt()), "ADMIN", 3000, "activo", 1);

            // Registrar Personas y Usuarios de alumnos A, B, C
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      4001, 44444441, "alumna@test.com", 111, "Alumno", "A", "Calle A");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo) VALUES (?, ?)", 4001, 44444441);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "44444441", BCrypt.hashpw("Alum123!", BCrypt.gensalt()), "ALUMNO", 4001, "activo", 1);

            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      4002, 44444442, "alumnb@test.com", 222, "Alumno", "B", "Calle B");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo) VALUES (?, ?)", 4002, 44444442);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "44444442", BCrypt.hashpw("Alum123!", BCrypt.gensalt()), "ALUMNO", 4002, "activo", 1);

            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      4003, 44444443, "alumnc@test.com", 333, "Alumno", "C", "Calle C");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo) VALUES (?, ?)", 4003, 44444443);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "44444443", BCrypt.hashpw("Alum123!", BCrypt.gensalt()), "ALUMNO", 4003, "activo", 1);

            // Inscribir Alumno C en Materia 401 para el año histórico 2025
            Base.exec("INSERT INTO inscripciones (fecha, id_alumno, id_materia, id_comision, condicion, anio_lectivo, cuatrimestre) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      "2025-03-01", 4003, 401, 411, "REGULAR", 2025, "1° Cuatrimestre");
        } finally {
            Base.close();
        }

        // 1. Iniciar sesión como Alumno A
        HttpResponse<String> loginA = loginAttempt("44444441", "Alum123!");
        assertEquals(302, loginA.statusCode());
        String cookieA = extractSessionId(loginA);

        // 2. Iniciar sesión como Alumno B
        HttpResponse<String> loginB = loginAttempt("44444442", "Alum123!");
        assertEquals(302, loginB.statusCode());
        String cookieB = extractSessionId(loginB);

        // 3. Inscribir Alumno A en Materia 401
        HttpRequest enrollA = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/inscripcion"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieA)
                .POST(ofForm(Map.of("id_materia", "401")))
                .build();
        HttpResponse<String> resEnrollA = client.send(enrollA, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resEnrollA.statusCode());

        // Verificar comisión asignada a Alumno A
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        String commNameA;
        try {
            Object comId = Base.firstCell("SELECT id_comision FROM inscripciones WHERE id_alumno = 4001 AND id_materia = 401");
            assertNotNull(comId);
            Object comName = Base.firstCell("SELECT nombre FROM comisiones WHERE id_comision = ?", comId);
            commNameA = comName.toString();
            // Debería ser Comisión 1 o Comisión 2
            assertTrue("Comisión 1".equals(commNameA) || "Comisión 2".equals(commNameA));
        } finally {
            Base.close();
        }

        // 4. Inscribir Alumno B en Materia 401 (Debería balancearse y elegir la otra comisión)
        HttpRequest enrollB = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/inscripcion"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieB)
                .POST(ofForm(Map.of("id_materia", "401")))
                .build();
        HttpResponse<String> resEnrollB = client.send(enrollB, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resEnrollB.statusCode());

        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Object comId = Base.firstCell("SELECT id_comision FROM inscripciones WHERE id_alumno = 4002 AND id_materia = 401");
            assertNotNull(comId);
            Object comName = Base.firstCell("SELECT nombre FROM comisiones WHERE id_comision = ?", comId);
            String commNameB = comName.toString();
            // Debe ser la comisión contraria a la de Alumno A
            assertNotEquals(commNameA, commNameB);
        } finally {
            Base.close();
        }

        // 5. Alumno A se inscribe en Materia 402 (Debe reutilizar el mismo nombre de comisión)
        HttpRequest enrollA2 = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/inscripcion"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieA)
                .POST(ofForm(Map.of("id_materia", "402")))
                .build();
        HttpResponse<String> resEnrollA2 = client.send(enrollA2, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resEnrollA2.statusCode());

        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Object comId = Base.firstCell("SELECT id_comision FROM inscripciones WHERE id_alumno = 4001 AND id_materia = 402");
            assertNotNull(comId);
            Object comName = Base.firstCell("SELECT nombre FROM comisiones WHERE id_comision = ?", comId);
            assertEquals(commNameA, comName.toString(), "Debe mantener la consistencia de comisión");
        } finally {
            Base.close();
        }

        // 6. Alumno A solicita cambiar su comisión a la comisión opuesta
        String targetCommName = "Comisión 1".equals(commNameA) ? "Comisión 2" : "Comisión 1";
        HttpRequest requestChange = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/inscripcion/solicitar-cambio"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieA)
                .POST(ofForm(Map.of("nueva_comision", targetCommName)))
                .build();
        HttpResponse<String> resRequest = client.send(requestChange, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resRequest.statusCode());

        // 7. Verificar que en la vista del Alumno A figure la solicitud pendiente
        HttpRequest getInsc = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/inscripcion"))
                .header("Cookie", cookieA)
                .GET()
                .build();
        HttpResponse<String> resInsc = client.send(getInsc, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resInsc.statusCode());
        assertTrue(resInsc.body().contains("Solicitud pendiente para cambiar a:"));
        assertTrue(resInsc.body().contains(targetCommName));

        // 8. Iniciar sesión como Administrador
        HttpResponse<String> loginAdmin = loginAttempt("41111111", "Admin123!");
        assertEquals(302, loginAdmin.statusCode());
        String adminCookie = extractSessionId(loginAdmin);

        // 9. Verificar que en la vista de pendientes del Administrador figure la solicitud
        HttpRequest getPendientes = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/admin/pendientes"))
                .header("Cookie", adminCookie)
                .GET()
                .build();
        HttpResponse<String> resPendientes = client.send(getPendientes, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resPendientes.statusCode());
        assertTrue(resPendientes.body().contains("Solicitudes de Cambio de Comisión"));
        assertTrue(resPendientes.body().contains(targetCommName));

        // 9.1 Verificar que en la vista de detalle de la materia figuren por defecto los alumnos de la última inscripción registrada (2026)
        HttpRequest getMateriaDetalle = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/planificacion/materia/401"))
                .header("Cookie", adminCookie)
                .GET()
                .build();
        HttpResponse<String> resMateriaDetalle = client.send(getMateriaDetalle, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resMateriaDetalle.statusCode());
        assertTrue(resMateriaDetalle.body().contains("Alumno"));
        assertTrue(resMateriaDetalle.body().contains("A"));
        assertTrue(resMateriaDetalle.body().contains("2026 - 1° Cuatrimestre"));
        // El Alumno C no debe figurar por defecto porque es de 2025
        assertFalse(resMateriaDetalle.body().contains("2025 - 1° Cuatrimestre"));

        // 9.2 Verificar que si se solicita explícitamente el año 2025, figura el Alumno C y no los de 2026
        HttpRequest getMateriaDetalle2025 = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/planificacion/materia/401?anio_lectivo=2025"))
                .header("Cookie", adminCookie)
                .GET()
                .build();
        HttpResponse<String> resMateriaDetalle2025 = client.send(getMateriaDetalle2025, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resMateriaDetalle2025.statusCode());
        assertTrue(resMateriaDetalle2025.body().contains("2025 - 1° Cuatrimestre"));
        assertFalse(resMateriaDetalle2025.body().contains("2026 - 1° Cuatrimestre"));

        // Obtener el id_solicitud generado
        int idSolicitud;
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Object idVal = Base.firstCell("SELECT id_solicitud FROM solicitudes_comision WHERE id_alumno = 4001 AND estado = 'PENDIENTE'");
            assertNotNull(idVal);
            idSolicitud = ((Number) idVal).intValue();
        } finally {
            Base.close();
        }

        // 10. Admin aprueba la solicitud
        HttpRequest approveRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/admin/solicitudes-comision/aprobar/" + idSolicitud))
                .header("Cookie", adminCookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resApprove = client.send(approveRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resApprove.statusCode());

        // 11. Verificar que AMBAS inscripciones de Alumno A se hayan actualizado a la nueva comisión y la solicitud esté aprobada
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Object comId1 = Base.firstCell("SELECT id_comision FROM inscripciones WHERE id_alumno = 4001 AND id_materia = 401");
            Object comName1 = Base.firstCell("SELECT nombre FROM comisiones WHERE id_comision = ?", comId1);
            assertEquals(targetCommName, comName1.toString());

            Object comId2 = Base.firstCell("SELECT id_comision FROM inscripciones WHERE id_alumno = 4001 AND id_materia = 402");
            Object comName2 = Base.firstCell("SELECT nombre FROM comisiones WHERE id_comision = ?", comId2);
            assertEquals(targetCommName, comName2.toString());

            Object state = Base.firstCell("SELECT estado FROM solicitudes_comision WHERE id_solicitud = ?", idSolicitud);
            assertEquals("APROBADO", state.toString());
        } finally {
            Base.close();
        }
    }

    @Test
    void test16_LargePhoneNumberSupport() throws Exception {
        // Limpiar registros previos con este DNI para evitar colisiones de clave única
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Base.exec("DELETE FROM users WHERE name = '55555555'");
            Base.exec("DELETE FROM persons WHERE dni = 55555555");
        } finally {
            Base.close();
        }

        Map<String, String> params = Map.of(
            "nombre", "Jorge",
            "apellido", "Gomez",
            "email", "jorge.gomez@test.com",
            "dni", "55555555",
            "tel", "0358155049885",
            "direccion", "Calle Falsa 123",
            "rol", "ALUMNO",
            "password", "SecurePassword123!",
            "confirm_password", "SecurePassword123!"
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/registro"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(ofForm(params))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());

        // Verificar en base de datos que el teléfono se guardó como un Long (64-bit) correcto sin truncation ni overflow
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            Object phoneObj = Base.firstCell("SELECT tel FROM persons WHERE dni = 55555555");
            assertNotNull(phoneObj);
            long phoneVal = ((Number) phoneObj).longValue();
            assertEquals(358155049885L, phoneVal);
        } finally {
            Base.close();
        }
    }

    @Test
    void test17_SubjectDuplicateCodeVerification() throws Exception {
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        int existingMateriaId;
        int anotherMateriaId;
        try {
            // 1. Registrar Administrador para iniciar sesión
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      5000, 45555555, "admin5@test.com", 111111, "Admin", "Cinco", "Admin Office");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "45555555", BCrypt.hashpw("Admin123!", BCrypt.gensalt()), "ADMIN", 5000, "activo", 1);

            // 2. Insertar una materia existente con código 'TEST-DUPE'
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      501, "TEST-DUPE", "Materia Duplicada", 4, "1° Cuatrimestre", 1);
            existingMateriaId = 501;

            // 3. Insertar otra materia con código 'TEST-DUPE-2'
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      502, "TEST-DUPE-2", "Materia Dos", 4, "1° Cuatrimestre", 1);
            anotherMateriaId = 502;
        } finally {
            Base.close();
        }

        // 4. Iniciar sesión como Admin
        HttpResponse<String> loginRes = loginAttempt("45555555", "Admin123!");
        assertEquals(302, loginRes.statusCode());
        String adminCookie = extractSessionId(loginRes);
        assertNotNull(adminCookie);

        // 5. Intentar crear una materia con código duplicado (TEST-DUPE)
        Map<String, String> createParams = Map.of(
            "codigo", "test-dupe", // Probamos minúsculas para verificar case-insensitivity
            "nombre", "Nueva Materia Duplicada",
            "carga_horaria", "4",
            "periodo", "1° Cuatrimestre",
            "anio", "1"
        );
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/planificacion/materia"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", adminCookie)
                .POST(ofForm(createParams))
                .build();
        HttpResponse<String> createRes = client.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, createRes.statusCode());
        String locationHeader = createRes.headers().firstValue("Location").orElse("");
        assertTrue(locationHeader.contains("/planificacion?error=C%C3%B3digo+existente.")
                || locationHeader.contains("/planificacion?error=C%C3%B3digo%20existente."),
                "Debería redirigir a planificacion con error de código existente. Location: " + locationHeader);

        // 6. Intentar editar la segunda materia para que tenga el código de la primera (TEST-DUPE)
        Map<String, String> editParams = Map.of(
            "id_materia", String.valueOf(anotherMateriaId),
            "codigo", "TEST-DUPE",
            "nombre", "Materia Modificada Duplicada",
            "carga_horaria", "4",
            "periodo", "1° Cuatrimestre",
            "anio", "1"
        );
        HttpRequest editReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/planificacion/materia/editar"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", adminCookie)
                .POST(ofForm(editParams))
                .build();
        HttpResponse<String> editRes = client.send(editReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, editRes.statusCode());
        String editLocationHeader = editRes.headers().firstValue("Location").orElse("");
        assertTrue(editLocationHeader.contains("/editar?error=C%C3%B3digo+existente.")
                || editLocationHeader.contains("/editar?error=C%C3%B3digo%20existente."),
                "Debería redirigir a editar con error de código existente. Location: " + editLocationHeader);
    }

    @Test
    void test18_AdminUserRemoval() throws Exception {
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        int studentUserId;
        int studentPersonId = 6001;
        try {
            // 1. Registrar Administrador para iniciar sesión
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      6000, 46666666, "admin6@test.com", 111111, "Admin", "Seis", "Admin Office");
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "46666666", BCrypt.hashpw("Admin123!", BCrypt.gensalt()), "ADMIN", 6000, "activo", 1);

            // 2. Registrar Alumno de prueba
            Base.exec("INSERT INTO persons (id_per, dni, mail, tel, nombre, apellido, direccion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      studentPersonId, 47777777, "alumn6@test.com", 222222, "Alumno", "ParaEliminar", "Calle Falsa");
            Base.exec("INSERT INTO alumnos (id_alumno, legajo) VALUES (?, ?)", studentPersonId, 47777777);
            Base.exec("INSERT INTO users (name, password, tipo_usuario, id_persona, estado, active) VALUES (?, ?, ?, ?, ?, ?)",
                      "47777777", BCrypt.hashpw("Alum123!", BCrypt.gensalt()), "ALUMNO", studentPersonId, "activo", 1);
            
            studentUserId = ((Number) User.findFirst("name = ?", "47777777").getId()).intValue();

            // 3. Crear registros hijos asociados (inscripciones, evaluaciones, solicitudes)
            // Materia & Comisión para asociar
            Base.exec("INSERT INTO materias (id_materia, codigo, nombre, carga_horaria, periodo, anio) VALUES (?, ?, ?, ?, ?, ?)",
                      601, "MAT-601", "Materia Seis", 4, "1° Cuatrimestre", 1);
            Base.exec("INSERT INTO comisiones (id_comision, id_materia, horario, nombre) VALUES (?, ?, ?, ?)",
                      611, 601, "Viernes 08:00 - 10:00", "Comisión 1");
            
            Base.exec("INSERT INTO inscripciones (id_inscripcion, fecha, id_alumno, id_materia, id_comision, condicion, anio_lectivo, cuatrimestre) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                      601, "2026-03-01", studentPersonId, 601, 611, "REGULAR", 2026, "1° Cuatrimestre");
            Base.exec("INSERT INTO evaluaciones (id_evaluacion, nota, fecha, id_alumno, id_comision) VALUES (?, ?, ?, ?, ?)",
                      601, 8, "2026-05-01", studentPersonId, 611);
            Base.exec("INSERT INTO solicitudes_comision (id_solicitud, id_alumno, comision_solicitada, fecha, estado, anio_lectivo, cuatrimestre) VALUES (?, ?, ?, ?, ?, ?, ?)",
                      601, studentPersonId, "Comisión 2", "2026-03-05", "PENDIENTE", 2026, "1° Cuatrimestre");
        } finally {
            Base.close();
        }

        // 4. Iniciar sesión como Admin
        HttpResponse<String> loginRes = loginAttempt("46666666", "Admin123!");
        assertEquals(302, loginRes.statusCode());
        String adminCookie = extractSessionId(loginRes);
        assertNotNull(adminCookie);

        // 5. Enviar POST para eliminar el alumno
        HttpRequest removeReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/admin/eliminar/" + studentUserId))
                .header("Cookie", adminCookie)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> removeRes = client.send(removeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, removeRes.statusCode());
        String locationHeader = removeRes.headers().firstValue("Location").orElse("");
        assertTrue(locationHeader.contains("/admin/usuarios"), "Debería redirigir a la lista de usuarios. Location: " + locationHeader);

        // 6. Verificar en base de datos que todos los registros se eliminaron en cascada
        Base.open("org.sqlite.JDBC", TEST_DB_URL, "", "");
        try {
            // Usuario y Persona eliminados
            assertNull(User.findById(studentUserId));
            assertNull(Person.findById(studentPersonId));
            
            // Registros hijos eliminados
            Object countInsc = Base.firstCell("SELECT COUNT(*) FROM inscripciones WHERE id_alumno = ?", studentPersonId);
            assertEquals(0L, ((Number) countInsc).longValue());

            Object countEval = Base.firstCell("SELECT COUNT(*) FROM evaluaciones WHERE id_alumno = ?", studentPersonId);
            assertEquals(0L, ((Number) countEval).longValue());

            Object countSol = Base.firstCell("SELECT COUNT(*) FROM solicitudes_comision WHERE id_alumno = ?", studentPersonId);
            assertEquals(0L, ((Number) countSol).longValue());

            Object countAlum = Base.firstCell("SELECT COUNT(*) FROM alumnos WHERE id_alumno = ?", studentPersonId);
            assertEquals(0L, ((Number) countAlum).longValue());
        } finally {
            Base.close();
        }
    }
}
