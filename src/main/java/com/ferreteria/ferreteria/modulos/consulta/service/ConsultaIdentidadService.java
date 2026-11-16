package com.ferreteria.ferreteria.modulos.consulta.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/** Consulta DNI y RUC en ApiPerú. El padrón es público; no es una conexión directa a RENIEC. */
@Service
public class ConsultaIdentidadService {

    private final RestClient cliente;
    private final String token;

    public ConsultaIdentidadService(@Value("${apiperu.url}") String url,
                                    @Value("${apiperu.token:}") String token) {
        this.token = token == null ? "" : token.trim();
        this.cliente = RestClient.builder().baseUrl(url).build();
    }

    public Map<String, String> dni(String dni) {
        if (!dni.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El DNI es incorrecto.");
        }
        Map<?, ?> data = consultar("/dni", Map.of("dni", dni), "DNI");
        return Map.of(
                "numero", texto(data.get("numero")),
                "nombres", texto(data.get("nombres")),
                "apellidoPaterno", texto(data.get("apellido_paterno")),
                "apellidoMaterno", texto(data.get("apellido_materno")),
                "nombreCompleto", unir(texto(data.get("nombres")), texto(data.get("apellido_paterno")), texto(data.get("apellido_materno")))
        );
    }

    public Map<String, String> ruc(String ruc) {
        if (!ruc.matches("(10|15|17|20)[0-9]{9}") || !rucConDigitoValido(ruc)) {
            throw new IllegalArgumentException("El RUC es incorrecto.");
        }
        Map<?, ?> data = consultar("/ruc", Map.of("ruc", ruc), "RUC");
        return Map.of(
                "numero", texto(data.get("ruc")),
                "razonSocial", texto(data.get("nombre_o_razon_social")),
                "direccion", texto(data.get("direccion")),
                "estado", texto(data.get("estado")),
                "condicion", texto(data.get("condicion"))
        );
    }

    private Map<?, ?> consultar(String ruta, Map<String, String> cuerpo, String documento) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Falta el token de consulta de documentos. El administrador debe ponerlo en el archivo de configuración.");
        }
        try {
            Map<?, ?> respuesta = cliente.post()
                    .uri(ruta)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null || !Boolean.TRUE.equals(respuesta.get("success")) || !(respuesta.get("data") instanceof Map<?, ?> data)) {
                throw new IllegalArgumentException("El " + documento + " es incorrecto.");
            }
            return data;
        } catch (RestClientResponseException e) {
            int estado = e.getStatusCode().value();
            if (estado == 400 || estado == 404) {
                throw new IllegalArgumentException("El " + documento + " es incorrecto.");
            }
            throw new IllegalArgumentException("No se pudo consultar el " + documento + ". Intente de nuevo.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("No se pudo consultar el " + documento + ". Intente de nuevo.");
        }
    }

    /** Dígito verificador del RUC (módulo 11 de SUNAT). */
    private boolean rucConDigitoValido(String ruc) {
        int[] factores = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += (ruc.charAt(i) - '0') * factores[i];
        }
        int resto = 11 - (suma % 11);
        int digito = resto == 10 ? 0 : resto == 11 ? 1 : resto;
        return digito == (ruc.charAt(10) - '0');
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private String unir(String... partes) {
        StringBuilder texto = new StringBuilder();
        for (String parte : partes) {
            if (parte == null || parte.isBlank()) continue;
            if (!texto.isEmpty()) texto.append(' ');
            texto.append(parte);
        }
        return texto.toString();
    }
}
