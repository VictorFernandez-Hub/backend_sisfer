package com.ferreteria.ferreteria.modulos.seguridad.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Envía los correos del sistema (código de recuperación y activación de cuenta).
 *
 * Render (plan gratuito) bloquea los puertos SMTP 25, 465 y 587, así que en producción
 * el correo sale por la API HTTPS de Brevo (puerto 443). Si no hay BREVO_API_KEY
 * (desarrollo local), se usa el SMTP de Gmail como antes.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final RestClient http;

    @Value("${app.mail.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${app.mail.from}")
    private String remitente;

    @Value("${app.mail.from-name:La Solución Ferretera}")
    private String nombreRemitente;

    public CorreoService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(10_000);
        fabrica.setReadTimeout(15_000);
        this.http = RestClient.builder().requestFactory(fabrica).build();
    }

    /** Envía un correo HTML. Lanza excepción si no se pudo, para que el llamador lo informe. */
    public void enviar(String para, String asunto, String html) {
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            enviarPorBrevo(para, asunto, html);
        } else {
            enviarPorSmtp(para, asunto, html);
        }
        log.info("Correo enviado a {} ({})", ocultar(para), asunto);
    }

    private void enviarPorBrevo(String para, String asunto, String html) {
        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of("name", nombreRemitente, "email", remitente),
                "to", List.of(Map.of("email", para)),
                "subject", asunto,
                "htmlContent", html
        );
        http.post()
                .uri(BREVO_URL)
                .header("api-key", brevoApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity();
    }

    private void enviarPorSmtp(String para, String asunto, String html) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("No hay proveedor de correo configurado (BREVO_API_KEY o spring.mail.*).");
        }
        try {
            MimeMessage mensaje = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente, nombreRemitente);
            helper.setTo(para);
            helper.setSubject(asunto);
            helper.setText(html, true);
            sender.send(mensaje);
        } catch (Exception e) {
            throw new IllegalStateException("Fallo SMTP: " + e.getMessage(), e);
        }
    }

    /** Para los logs: "ju***@gmail.com". */
    public static String ocultar(String correo) {
        if (correo == null) return "";
        int arroba = correo.indexOf('@');
        if (arroba <= 2) return "***" + (arroba >= 0 ? correo.substring(arroba) : "");
        return correo.substring(0, 2) + "***" + correo.substring(arroba);
    }
}
