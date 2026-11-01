package com.ferreteria.ferreteria.modulos.seguridad.controller;

import com.ferreteria.ferreteria.modulos.seguridad.dto.AuthResponseDTO;
import com.ferreteria.ferreteria.modulos.seguridad.dto.LoginRequestDTO;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
    // Envía el correo con el código
    private final JavaMailSender mailSender;
    
    // Guarda el código de cada correo y su hora de vencimiento (3 minutos)
    private final Map<String, CodigoRecuperacion> codigos = new ConcurrentHashMap<>();

    private final BitacoraRepository bitacoraRepository;

    // Spring entrega estos objetos al crear el controlador
    public AuthController(AuthenticationManager authenticationManager,
                          UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          BitacoraRepository bitacoraRepository,
                          JavaMailSender mailSender) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.bitacoraRepository = bitacoraRepository;
        this.mailSender = mailSender;
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

            Bitacora b = new Bitacora();
            b.setIdUsuario(ingresado.getIdUsuario());
            b.setFecEvento(java.time.LocalDateTime.now());
            b.setModulo("SEGURIDAD");
            b.setAccion("LOGIN");
            b.setTablaObj("usuario");
            b.setIdRegistro(String.valueOf(ingresado.getIdUsuario()));
            b.setDetalle("Ingresó al sistema");
            b.setIpOrigen("local");
            b.setExito(true);
            bitacoraRepository.save(b);
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
        return ResponseEntity.status(403).body(
                "Usuario bloqueado. Comunícate con el área de sistemas para la revisión."
        );
    } catch (org.springframework.security.authentication.DisabledException e) {
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
                return ResponseEntity.status(403).body(
                        "Usuario bloqueado por intentos fallidos. Comunícate con el área de sistemas."
                );
            }
            usuarioRepository.save(fallido);
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
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Sesión cerrada exitosamente");
    }

    // Recibe el correo, busca si existe en la base y envía un código de 6 dígitos
    @PostMapping("/solicitar-codigo")
    public ResponseEntity<String> solicitarCodigo(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(404).body("El correo no está registrado.");
        }

        String codigo = String.format("%06d", new Random().nextInt(1_000_000));
        codigos.put(email.toLowerCase(), new CodigoRecuperacion(codigo, LocalDateTime.now().plusMinutes(3)));

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom("ferreteria.lasolucion53@gmail.com");
            helper.setTo(email);
            helper.setSubject("Código para recuperar tu clave - La Solución Ferretera");

            String html = """
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
                    """.formatted(usuario.getNombres(), codigo);

            helper.setText(html, true);
            mailSender.send(mensaje);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("No se pudo enviar el código.");
        }

        return ResponseEntity.ok("Código enviado.");
    }

    // Compara el código escrito con el que se envió. Si venció o no coincide, lo rechaza
    @PostMapping("/validar-codigo")
    public ResponseEntity<String> validarCodigo(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String codigo = body.getOrDefault("codigo", "").trim();
        CodigoRecuperacion guardado = codigos.get(email);

        if (guardado == null || LocalDateTime.now().isAfter(guardado.vence)) {
            return ResponseEntity.status(400).body("El código venció. Solicite uno nuevo.");
        }
        if (!guardado.codigo.equals(codigo)) {
            return ResponseEntity.status(400).body("Código incorrecto. Revise el código e intente nuevamente.");
        }
        return ResponseEntity.ok("Código correcto.");
    }

    // Cambia la clave solo si el código sigue vigente
    @PostMapping("/cambiar-clave")
    public ResponseEntity<String> cambiarClave(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String codigo = body.getOrDefault("codigo", "").trim();
        String claveNueva = body.getOrDefault("claveNueva", "");

        CodigoRecuperacion guardado = codigos.get(email);
        if (guardado == null || LocalDateTime.now().isAfter(guardado.vence) || !guardado.codigo.equals(codigo)) {
            return ResponseEntity.status(400).body("El código no es válido o ya venció.");
        }

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null) {
            return ResponseEntity.status(404).body("El correo no está registrado.");
        }

        usuario.setHashClave(passwordEncoder.encode(claveNueva));
        usuarioRepository.save(usuario);
        codigos.remove(email);
        return ResponseEntity.ok("Clave actualizada. Ya puede iniciar sesión.");
    }

    // Guarda el código y la hora en que deja de servir
    private static class CodigoRecuperacion {
        final String codigo;
        final LocalDateTime vence;

        CodigoRecuperacion(String codigo, LocalDateTime vence) {
            this.codigo = codigo;
            this.vence = vence;
        }
    }
}