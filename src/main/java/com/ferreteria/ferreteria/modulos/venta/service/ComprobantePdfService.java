package com.ferreteria.ferreteria.modulos.venta.service;

import com.ferreteria.ferreteria.modulos.venta.dto.VentaDetalle;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Arma el voucher en PDF con el ancho de un ticket de 80 mm.
 * El alto depende de cuántos productos tenga la venta: primero se dibuja en una hoja larga para
 * medir cuánto ocupa y luego se vuelve a dibujar en una hoja de esa medida exacta.
 * Sirve para boleta, factura (con su estado) y para la proforma de una venta en borrador.
 */
@Service
public class ComprobantePdfService {

    private static final String EMPRESA = "LA SOLUCIÓN FERRETERA";
    private static final String DIRECCION = "Km. 08 Carretera Piura – Chulucanas";

    private static final float ANCHO = 226.8f; // 80 mm
    private static final float ALTO_DE_MEDICION = 14000f;
    private static final float MARGEN = 12f;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color ROJO = new Color(176, 0, 32);
    private static final Color GRIS = new Color(90, 90, 90);

    private static final Font NEGRITA_GRANDE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 11f);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 8f);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, 8f);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, 6.5f, Font.NORMAL, GRIS);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 10f);
    private static final Font AVISO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 10f, Font.NORMAL, ROJO);

    public byte[] generar(VentaDetalle v, BigDecimal tasaIgv) {
        try {
            float alto = dibujar(v, tasaIgv, ALTO_DE_MEDICION).ocupado() + MARGEN;
            Dibujo dibujo = dibujar(v, tasaIgv, alto);
            // El voucher siempre va en una sola hoja: si el pie no entró, se alarga un poco
            while (dibujo.paginas() > 1) {
                alto += 10f;
                dibujo = dibujar(v, tasaIgv, alto);
            }
            return dibujo.pdf();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la venta " + v.numero(), e);
        }
    }

    private record Dibujo(byte[] pdf, float ocupado, int paginas) {}

    private Dibujo dibujar(VentaDetalle v, BigDecimal tasaIgv, float alto) throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(new Rectangle(ANCHO, alto), MARGEN, MARGEN, MARGEN, MARGEN);
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        doc.addTitle(titulo(v));
        doc.addCreator(EMPRESA);
        doc.open();

        cabecera(doc, v);
        datosDeLaVenta(doc, v);
        linea(doc);
        productos(doc, v);
        linea(doc);
        totales(doc, v, tasaIgv);
        pie(doc, v);

        float ocupado = alto - writer.getVerticalPosition(true);
        int paginas = writer.getPageNumber();
        doc.close();
        return new Dibujo(salida.toByteArray(), ocupado, paginas);
    }

    private void cabecera(Document doc, VentaDetalle v) throws DocumentException {
        doc.add(centrado(EMPRESA, NEGRITA_GRANDE, 0f));
        doc.add(centrado(DIRECCION, PEQUENA, 2f));

        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        caja.setSpacingBefore(7f);
        caja.setSpacingAfter(7f);
        PdfPCell celda = new PdfPCell();
        celda.setPadding(5f);
        celda.setBorderWidth(0.8f);
        Paragraph tipo = new Paragraph(v.comprobante() != null ? v.comprobante().nombre().toUpperCase() : "PROFORMA", NEGRITA);
        tipo.setAlignment(Element.ALIGN_CENTER);
        Paragraph numero = new Paragraph(v.comprobante() != null ? v.comprobante().numeroCompleto() : v.numero(), TOTAL);
        numero.setAlignment(Element.ALIGN_CENTER);
        celda.addElement(tipo);
        celda.addElement(numero);
        caja.addCell(celda);
        doc.add(caja);

        if (v.comprobante() != null && "ANULADO".equals(v.comprobante().estado())) {
            PdfPTable aviso = new PdfPTable(1);
            aviso.setWidthPercentage(100);
            aviso.setSpacingAfter(7f);
            PdfPCell texto = new PdfPCell(new Phrase("COMPROBANTE ANULADO", AVISO));
            texto.setHorizontalAlignment(Element.ALIGN_CENTER);
            texto.setPadding(4f);
            texto.setBorderColor(ROJO);
            texto.setBorderWidth(1.2f);
            aviso.addCell(texto);
            doc.add(aviso);
        }
    }

    private void datosDeLaVenta(Document doc, VentaDetalle v) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{50f, 152.8f});
        t.setWidthPercentage(100);
        String fecha = (v.comprobante() != null ? v.comprobante().fecEmision() : v.fecVenta()).format(FECHA);
        dato(t, "Fecha", fecha);
        dato(t, "Cliente", v.cliente());
        dato(t, v.tipoDocCliente(), v.numDocCliente());
        if (v.direccionCliente() != null && !v.direccionCliente().isBlank()) {
            dato(t, "Dirección", v.direccionCliente());
        }
        dato(t, "Pago", v.metodo());
        dato(t, "Atendió", v.usuario());
        if (v.comprobante() != null) {
            dato(t, "Venta", v.numero());
        }
        doc.add(t);
    }

    private void dato(PdfPTable t, String nombre, String valor) {
        t.addCell(celda(nombre, PEQUENA, Element.ALIGN_LEFT, 1.5f));
        t.addCell(celda(valor, NORMAL, Element.ALIGN_LEFT, 1.5f));
    }

    private void productos(Document doc, VentaDetalle v) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{34f, 90f, 37f, 41.8f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.addCell(celda("Cant.", NEGRITA, Element.ALIGN_LEFT, 2f));
        t.addCell(celda("Descripción", NEGRITA, Element.ALIGN_LEFT, 2f));
        t.addCell(celda("P. unit.", NEGRITA, Element.ALIGN_RIGHT, 2f));
        t.addCell(celda("Importe", NEGRITA, Element.ALIGN_RIGHT, 2f));

        for (VentaDetalle.Linea l : v.lineas()) {
            t.addCell(celda(l.cantidad().stripTrailingZeros().toPlainString() + " " + l.unidad(), NORMAL, Element.ALIGN_LEFT, 2f));

            PdfPCell descripcion = celda(null, NORMAL, Element.ALIGN_LEFT, 2f);
            Paragraph nombre = new Paragraph(l.producto(), NORMAL);
            nombre.setLeading(9f);
            Paragraph codigo = new Paragraph(l.codigo(), PEQUENA);
            codigo.setLeading(8f);
            descripcion.addElement(nombre);
            descripcion.addElement(codigo);
            t.addCell(descripcion);

            t.addCell(celda(dinero(l.precioUnit()), NORMAL, Element.ALIGN_RIGHT, 2f));
            t.addCell(celda(dinero(l.totLinea()), NORMAL, Element.ALIGN_RIGHT, 2f));
        }
        doc.add(t);
    }

    private void totales(Document doc, VentaDetalle v, BigDecimal tasaIgv) throws DocumentException {
        String porcentaje = tasaIgv.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
        PdfPTable t = new PdfPTable(new float[]{130f, 72.8f});
        t.setWidthPercentage(100);
        total(t, "Op. gravada", "S/ " + dinero(v.totSubtotal()), NORMAL);
        total(t, "IGV (" + porcentaje + "%)", "S/ " + dinero(v.totIgv()), NORMAL);
        total(t, "TOTAL", "S/ " + dinero(v.totTotal()), TOTAL);
        doc.add(t);

        Paragraph letras = new Paragraph("SON: " + MontoEnLetras.convertir(v.totTotal()), NEGRITA);
        letras.setSpacingBefore(5f);
        letras.setLeading(10f);
        doc.add(letras);
    }

    private void total(PdfPTable t, String nombre, String valor, Font fuente) {
        t.addCell(celda(nombre, fuente, Element.ALIGN_RIGHT, 1.5f));
        t.addCell(celda(valor, fuente, Element.ALIGN_RIGHT, 1.5f));
    }

    private void pie(Document doc, VentaDetalle v) throws DocumentException {
        if (v.observacion() != null && !v.observacion().isBlank()) {
            Paragraph obs = new Paragraph("Observación: " + v.observacion(), PEQUENA);
            obs.setSpacingBefore(6f);
            obs.setLeading(8f);
            doc.add(obs);
        }
        linea(doc);
        if (v.comprobante() != null) {
            doc.add(centrado("¡Gracias por su compra!", NEGRITA, 0f));
            doc.add(centrado("Documento sin valor tributario.", PEQUENA, 2f));
        } else {
            doc.add(centrado("Este documento no es un comprobante de pago.", NEGRITA, 0f));
            doc.add(centrado("Los precios incluyen IGV. El stock se descuenta recién al cobrar.", PEQUENA, 2f));
        }
    }

    private String titulo(VentaDetalle v) {
        return v.comprobante() != null ? v.comprobante().numeroCompleto() : "Proforma " + v.numero();
    }

    private void linea(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(3f);
        t.setSpacingAfter(5f);
        PdfPCell c = new PdfPCell();
        c.setFixedHeight(1f);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(0.5f);
        c.setBorderColorBottom(GRIS);
        t.addCell(c);
        doc.add(t);
    }

    private Paragraph centrado(String texto, Font fuente, float espacioAntes) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(espacioAntes);
        p.setLeading(fuente.getSize() + 2f);
        return p;
    }

    private PdfPCell celda(String texto, Font fuente, int alineacion, float relleno) {
        PdfPCell c = texto == null ? new PdfPCell() : new PdfPCell(new Phrase(texto, fuente));
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(alineacion);
        c.setPaddingTop(relleno);
        c.setPaddingBottom(relleno);
        c.setPaddingLeft(0f);
        c.setPaddingRight(0f);
        return c;
    }

    private String dinero(BigDecimal n) {
        return String.format(java.util.Locale.US, "%,.2f", n);
    }
}
