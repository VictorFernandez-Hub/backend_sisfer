package com.ferreteria.ferreteria.modulos.seguridad.controller;

import com.ferreteria.ferreteria.modulos.seguridad.dto.AuthResponseDTO;
import com.ferreteria.ferreteria.modulos.seguridad.dto.LoginRequestDTO;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import com.ferreteria.ferreteria.modulos.seguridad.service.CorreoService;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ferreteria.ferreteria.modulos.seguridad.model.Bitacora;
import com.ferreteria.ferreteria.modulos.seguridad.repository.BitacoraRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Valida usuario y clave en el login
    private final AuthenticationManager authenticationManager;
    // Consulta y guarda usuarios en la base
    private final UsuarioRepository usuarioRepository;
    // Encripta la clave antes de guardarla
    private final PasswordEncoder passwordEncoder;
    // Envía el correo con el código (API de Brevo en producción, SMTP en local)
    private final CorreoService correoService;

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    // Generador criptográficamente seguro: el código no se puede predecir
    private static final SecureRandom RANDOM = new SecureRandom();

    // Guarda el código de cada correo y su hora de vencimiento (3 minutos)
    private final Map<String, CodigoRecuperacion> codigos = new ConcurrentHashMap<>();

    private final BitacoraRepository bitacoraRepository;
    // Registra en la bitácora inicios y cierres de sesión, intentos fallidos y bloqueos
    private final BitacoraService bitacora;

    // Atributo de sesión: evita registrar dos veces el cierre (logout + sesión destruida)
    public static final String CIERRE_REGISTRADO = "BITACORA_CIERRE_REGISTRADO";

    // Spring entrega estos objetos al crear el controlador
    public AuthController(AuthenticationManager authenticationManager,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          BitacoraRepository bitacoraRepository,
                          CorreoService correoService,
                          BitacoraService bitacora) {
        this.bitacora = bitacora;
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.bitacoraRepository = bitacoraRepository;
        this.correoService = correoService;
    }

    // Inicia sesión. Si el usuario está bloqueado o inactivo, responde con el mensaje correspondiente
      @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequest,
                               HttpServletRequest request) {
        try {
            Usuario porVencer = usuarioRepository.findByCuenta(loginRequest.getCuenta()).orElse(null);
        if (porVencer != null
                && "PLAZO_FIJO".equalsIgnoreCase(porVencer.getTipoContrato())
                && porVencer.getFecFin() != null
                && porVencer.getFecFin().isBefore(java.time.LocalDate.now())
                && "ACTIVO".equalsIgnoreCase(porVencer.getEstado())) {
            porVencer.setEstado("INACTIVO");
            usuarioRepository.save(porVencer);
            bitacora.registrarUsuario(porVencer.getIdUsuario(), "SEGURIDAD", "LOGIN_RECHAZADO",
                    "Intentó ingresar con el contrato vencido (" + porVencer.getFecFin() + "). La cuenta pasó a INACTIVO", false);
            return ResponseEntity.status(403).body(
                    "Su contrato ha finalizado y su cuenta quedó inactiva. Comuníquese con el administrador del negocio para renovarlo."
            );
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getCuenta(), loginRequest.getClave())
        );

        Usuario ingresado = usuarioRepository.findByCuenta(loginRequest.getCuenta()).orElse(null);
        if (ingresado != null) {
            ingresado.setNumIntentos((short) 0);
            usuarioRepository.save(ingresado);

            String rol = ingresado.getRol() != null ? ingresado.getRol().getCodigo() : "sin rol";
            bitacora.registrarUsuario(ingresado.getIdUsuario(), "SEGURIDAD", "LOGIN",
                    "Inició sesión con el rol " + rol, true);
            }

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new AuthResponseDTO(
                authentication.getName(),
                "Inicio de sesión exitoso",
                roles
        ));
    } catch (org.springframework.security.authentication.LockedException e) {
        registrarRechazo(loginRequest.getCuenta(), "Intentó ingresar con la cuenta BLOQUEADA");
        return ResponseEntity.status(403).body(
                "Usuario bloqueado. Comunícate con el área de sistemas para la revisión."
        );
    } catch (org.springframework.security.authentication.DisabledException e) {
        registrarRechazo(loginRequest.getCuenta(), "Intentó ingresar con la cuenta INACTIVA");
        return ResponseEntity.status(403).body(
                "Usuario inactivo. Comunícate con el administrador del negocio."
        );
    } catch (org.springframework.security.core.AuthenticationException e) {
        Usuario fallido = usuarioRepository.findByCuenta(loginRequest.getCuenta()).orElse(null);
        if (fallido != null && "ACTIVO".equalsIgnoreCase(fallido.getEstado())) {
            int intentos = (fallido.getNumIntentos() == null ? 0 : fallido.getNumIntentos()) + 1;
            fallido.setNumIntentos((short) intentos);
            if (intentos >= 3) {
                fallido.setEstado("BLOQUEADO");
                usuarioRepository.save(fallido);
                bitacora.registrarUsuario(fallido.getIdUsuario(), "SEGURIDAD", "BLOQUEO",
                        "Cuenta bloqueada por 3 intentos fallidos de contraseña", false);
                return ResponseEntity.status(403).body(
                        "Usuario bloqueado por intentos fallidos. Comunícate con el área de sistemas."
                );
            }
            usuarioRepository.save(fallido);
            bitacora.registrarUsuario(fallido.getIdUsuario(), "SEGURIDAD", "LOGIN_FALLIDO",
                    "Contraseña incorrecta. Intento " + intentos + " de 3", false);
            return ResponseEntity.status(401).body(
                    "Usuario o contraseña incorrectos. Intento " + intentos + " de 3."
            );
        }
        return ResponseEntity.status(401).body("Usuario o contraseña incorrectos.");
    }
}                                                                             
                                                                           
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual != null && actual.isAuthenticated() && !"anonymousUser".equals(actual.getName())) {
            usuarioRepository.findByCuenta(actual.getName()).ifPresent(u ->
                    bitacora.registrarUsuario(u.getIdUsuario(), "SEGURIDAD", "LOGOUT", "Cerró sesión", true));
        }
        if (session != null) {
            session.setAttribute(CIERRE_REGISTRADO, Boolean.TRUE);
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Sesión cerrada exitosamente");
    }

    // ===================== RECUPERACIÓN DE CLAVE =====================
    // Flujo: solicitar-codigo -> validar-codigo -> cambiar-clave.
    // El código vive en memoria 3 minutos; si el backend se reinicia, el usuario pide otro.

    private static final int MINUTOS_VIGENCIA = 3;
    private static final int SEGUNDOS_ESPERA_REENVIO = 60;
    private static final int MAX_INTENTOS_CODIGO = 5;

    // Recibe el correo, busca si existe en la base y envía un código de 6 dígitos
    @PostMapping("/solicitar-codigo")
    public ResponseEntity<String> solicitarCodigo(@RequestBody Map<String, String> body) {
        String email = normalizar(body.get("email"));
        if (email.isEmpty() || !email.contains("@")) {
            return ResponseEntity.badRequest().body("Ingrese un correo válido.");
        }

        List<Usuario> usuarios = usuarioRepository.findAllByEmailIgnoreCase(email);
        if (usuarios.isEmpty()) {
            return ResponseEntity.status(404).body("El correo no está registrado.");
        }
        if (usuarios.size() > 1) {
            log.warn("Recuperación rechazada: {} cuentas comparten el correo {}", usuarios.size(), CorreoService.ocultar(email));
            return ResponseEntity.status(409).body(
                    "Este correo está asociado a varias cuentas. Comuníquese con el área de sistemas.");
        }
        Usuario usuario = usuarios.get(0);

        // Evita reenvíos seguidos (protege la cuota de correos y al usuario de spam)
        CodigoRecuperacion previo = codigos.get(email);
        if (previo != null && previo.creado.plusSeconds(SEGUNDOS_ESPERA_REENVIO).isAfter(LocalDateTime.now())) {
            long faltan = java.time.Duration.between(LocalDateTime.now(),
                    previo.creado.plusSeconds(SEGUNDOS_ESPERA_REENVIO)).getSeconds() + 1;
            return ResponseEntity.status(429).body("Espere " + faltan + " segundos para pedir otro código.");
        }

        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        CodigoRecuperacion nuevo = new CodigoRecuperacion(codigo, usuario.getIdUsuario());
        codigos.put(email, nuevo);

        try {
            correoService.enviar(usuario.getEmail(),
                    "Código para recuperar tu clave - La Solución Ferretera",
                    htmlCodigo(usuario.getNombres(), codigo));
        } catch (Exception e) {
            codigos.remove(email, nuevo);
            log.error("No se pudo enviar el código de recuperación a {}: {}", CorreoService.ocultar(email), e.getMessage(), e);
            return ResponseEntity.status(502).body(
                    "No se pudo enviar el correo en este momento. Intente nuevamente en unos minutos.");
        }

        bitacora.registrarUsuario(usuario.getIdUsuario(), "SEGURIDAD", "SOLICITAR_CODIGO",
                "Pidió un código para recuperar la clave", true);
        return ResponseEntity.ok("Código enviado.");
    }

    // Compara el código escrito con el que se envió. Si venció o no coincide, lo rechaza
    @PostMapping("/validar-codigo")
    public ResponseEntity<String> validarCodigo(@RequestBody Map<String, String> body) {
        String error = verificarCodigo(normalizar(body.get("email")), texto(body.get("codigo")));
        return error == null ? ResponseEntity.ok("Código correcto.") : ResponseEntity.badRequest().body(error);
    }

    // Cambia la clave solo si el código sigue vigente
    @PostMapping("/cambiar-clave")
    public ResponseEntity<String> cambiarClave(@RequestBody Map<String, String> body) {
        String email = normalizar(body.get("email"));
        String claveNueva = body.getOrDefault("claveNueva", "");

        String error = verificarCodigo(email, texto(body.get("codigo")));
        if (error != null) {
            return ResponseEntity.badRequest().body(error);
        }
        // Las mismas reglas que muestra el frontend; el backend no confía en el navegador
        if (claveNueva.length() < 8 || !claveNueva.matches(".*[A-Z].*")
                || !claveNueva.matches(".*[0-9].*") || !claveNueva.matches(".*[^A-Za-z0-9].*")) {
            return ResponseEntity.badRequest().body(
                    "La clave debe tener mínimo 8 caracteres, una mayúscula, un número y un símbolo.");
        }

        CodigoRecuperacion guardado = codigos.get(email);
        if (guardado == null) {
            return ResponseEntity.badRequest().body("El código venció. Solicite uno nuevo.");
        }
        Usuario usuario = usuarioRepository.findById(guardado.idUsuario).orElse(null);
        if (usuario == null) {
            codigos.remove(email);
            return ResponseEntity.status(404).body("El correo no está registrado.");
        }

        usuario.setHashClave(passwordEncoder.encode(claveNueva));
        usuario.setNumIntentos((short) 0);
        usuarioRepository.save(usuario);
        codigos.remove(email);

        bitacora.registrarUsuario(usuario.getIdUsuario(), "SEGURIDAD", "RECUPERAR_CLAVE",
                "Cambió su clave con el código enviado al correo", true);

        return ResponseEntity.ok("Clave actualizada. Ya puede iniciar sesión.");
    }

    // Inicio de sesión rechazado de una cuenta que existe (bloqueada o inactiva)
    private void registrarRechazo(String cuenta, String detalle) {
        if (cuenta == null) return;
        usuarioRepository.findByCuenta(cuenta).ifPresent(u ->
                bitacora.registrarUsuario(u.getIdUsuario(), "SEGURIDAD", "LOGIN_RECHAZADO", detalle, false));
    }

    // Devuelve null si el código es válido, o el mensaje de error. Tras 5 fallos el código se anula
    private String verificarCodigo(String email, String codigo) {
        CodigoRecuperacion guardado = codigos.get(email);
        if (guardado == null || LocalDateTime.now().isAfter(guardado.vence)) {
            codigos.remove(email);
            return "El código venció. Solicite uno nuevo.";
        }
        if (!guardado.codigo.equals(codigo)) {
            int fallos = guardado.fallos.incrementAndGet();
            if (fallos >= MAX_INTENTOS_CODIGO) {
                codigos.remove(email);
                return "Demasiados intentos. Solicite un código nuevo.";
            }
            return "Código incorrecto. Le quedan " + (MAX_INTENTOS_CODIGO - fallos) + " intentos.";
        }
        return null;
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String htmlCodigo(String nombres, String codigo) {
        return """
                <div style="background:#f4f4f5; padding:32px 12px; font-family:Arial, sans-serif;">
                  <div style="max-width:460px; margin:auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,.06);">
                    <div style="background:#f97316; padding:22px 28px; text-align:center;">
                      <div style="color:#fff; font-size:13px; letter-spacing:2px;">LA SOLUCIÓN</div>
                      <div style="color:#fff; font-size:22px; font-weight:bold;">Ferretera</div>
                    </div>
                    <div style="padding:32px 28px; color:#1f2937;">
                      <p style="margin:0 0 8px;">Hola <strong>%s</strong>,</p>
                      <p style="margin:0 0 20px; color:#4b5563;">Usa este código para crear tu nueva clave. Nadie de la ferretería te lo pedirá.</p>
                      <div style="background:#fff7ed; border:1px dashed #f97316; border-radius:10px; text-align:center; padding:18px;">
                        <div style="font-size:12px; color:#9a3412; letter-spacing:1px;">TU CÓDIGO</div>
                        <div style="font-size:34px; font-weight:bold; letter-spacing:8px; color:#f97316; margin-top:6px;">%s</div>
                      </div>
                      <p style="margin:20px 0 0; font-size:14px; color:#4b5563;">Vence en <strong>3 minutos</strong>. Si no pediste este cambio, ignora el mensaje.</p>
                    </div>
                    <div style="background:#fafafa; padding:14px 28px; text-align:center; font-size:12px; color:#9ca3af;">
                      La Solución Ferretera · Km. 08 Carretera Piura – Chulucanas
                    </div>
                  </div>
                </div>
                """.formatted(nombres == null ? "" : org.springframework.web.util.HtmlUtils.htmlEscape(nombres), codigo);
    }

    // Guarda el código, a qué usuario pertenece, cuándo se creó, cuándo vence y cuántas veces se falló
    private static class CodigoRecuperacion {
        final String codigo;
        final Integer idUsuario;
        final LocalDateTime creado = LocalDateTime.now();
        final LocalDateTime vence = creado.plusMinutes(MINUTOS_VIGENCIA);
        final java.util.concurrent.atomic.AtomicInteger fallos = new java.util.concurrent.atomic.AtomicInteger();

        CodigoRecuperacion(String codigo, Integer idUsuario) {
            this.codigo = codigo;
            this.idUsuario = idUsuario;
        }
    }

    @PostMapping("/activar")
public ResponseEntity<String> activar(@RequestBody Map<String, String> datos) {
    String token = datos.get("token");
    String clave = datos.get("clave");
    Usuario u = usuarioRepository.findByTokenActivacion(token).orElse(null);
    if (u == null || u.getFecToken() == null || u.getFecToken().isBefore(LocalDateTime.now())) {
        return ResponseEntity.badRequest().body("Enlace inválido o vencido.");
    }
    u.setHashClave(passwordEncoder.encode(clave));
    u.setEstado("ACTIVO");
    u.setTokenActivacion(null);
    u.setFecToken(null);
    usuarioRepository.save(u);
    return ResponseEntity.ok("Cuenta activada.");
}
}