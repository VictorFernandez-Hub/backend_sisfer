package com.ferreteria.ferreteria.modulos.reporte.service;

import com.ferreteria.ferreteria.modulos.reporte.dto.ProductoVendido;
import com.ferreteria.ferreteria.modulos.reporte.dto.RangoReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.ResumenReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorMetodo;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorVendedor;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reporte de ventas del periodo en hoja A4.
 * El encabezado sigue al del comprobante (ComprobantePdfService): empresa, dirección y un recuadro con el título.
 */
@Service
public class ReportePdfService {

    private static final String EMPRESA = "LA SOLUCIÓN FERRETERA";
    private static final String DIRECCION = "Km. 08 Carretera Piura – Chulucanas";

    private static final Set<String> NUMERICAS = Set.of("Ventas", "Monto", "% del total", "Cantidad");

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color GRIS = new Color(90, 90, 90);
    private static final Color FONDO_CABECERA = new Color(31, 31, 31);
    private static final Color NARANJA = new Color(255, 165, 0);
    private static final Color LINEA = new Color(210, 210, 210);

    private static final Font NEGRITA_GRANDE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 16f);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 12f);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 11f);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 9f);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, 9f);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, 8f, Font.NORMAL, GRIS);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 9f, Font.NORMAL, Color.WHITE);
    private static final Font CIFRA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, BaseFont.CP1252, 13f);

    public byte[] generar(RangoReporte rango, ResumenReporte resumen, List<VentaPorMetodo> metodos,
                          List<ProductoVendido> productos, List<VentaPorVendedor> vendedores) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40f, 40f, 36f, 36f);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.addTitle("Reporte de ventas " + rango.desde().format(FECHA) + " - " + rango.hasta().format(FECHA));
            doc.addCreator(EMPRESA);
            doc.open();

            cabecera(doc, "REPORTE DE VENTAS", rango);
            seccionResumen(doc, resumen);
            seccionMetodos(doc, metodos, resumen.totalVendido());
            seccionProductos(doc, productos);
            seccionVendedores(doc, vendedores);
            pie(doc, "Solo se consideran ventas confirmadas.");
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------ secciones

    private void pie(Document doc, String nota) throws DocumentException {
        Paragraph pie = new Paragraph(nota + " Generado el " + LocalDateTime.now().format(FECHA_HORA) + ".", PEQUENA);
        pie.setSpacingBefore(14f);
        doc.add(pie);
    }

    private void cabecera(Document doc, String nombreReporte, RangoReporte rango) throws DocumentException {
        doc.add(centrado(EMPRESA, NEGRITA_GRANDE, 0f));
        doc.add(centrado(DIRECCION, PEQUENA, 2f));

        PdfPTable caja = new PdfPTable(1);
        caja.setWidthPercentage(100);
        caja.setSpacingBefore(10f);
        caja.setSpacingAfter(12f);
        PdfPCell celda = new PdfPCell();
        celda.setPadding(7f);
        celda.setBorderWidth(0.8f);
        Paragraph titulo = new Paragraph(nombreReporte, TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        Paragraph periodo = new Paragraph("Del " + rango.desde().format(FECHA) + " al " + rango.hasta().format(FECHA), NORMAL);
        periodo.setAlignment(Element.ALIGN_CENTER);
        celda.addElement(titulo);
        celda.addElement(periodo);
        caja.addCell(celda);
        doc.add(caja);
    }

    private void seccionResumen(Document doc, ResumenReporte r) throws DocumentException {
        doc.add(tituloSeccion("1. Resumen"));
        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);
        t.setSpacingAfter(6f);
        tarjeta(t, "Total vendido", "S/ " + dinero(r.totalVendido()));
        tarjeta(t, "Ventas confirmadas", String.valueOf(r.numVentas()));
        tarjeta(t, "Ventas anuladas", r.numAnuladas() + "  (S/ " + dinero(r.montoAnuladas()) + ")");
        doc.add(t);
    }

    private void seccionMetodos(Document doc, List<VentaPorMetodo> lista, BigDecimal total) throws DocumentException {
        doc.add(tituloSeccion("2. Cobrado por medio de pago"));
        if (lista.isEmpty()) {
            doc.add(sinDatos("Sin ventas confirmadas en este periodo."));
            return;
        }
        PdfPTable t = tabla(new float[]{50f, 15f, 20f, 15f}, "Medio de pago", "Ventas", "Monto", "% del total");
        for (VentaPorMetodo m : lista) {
            t.addCell(celda(m.metodo(), Element.ALIGN_LEFT));
            t.addCell(celda(String.valueOf(m.ventas()), Element.ALIGN_RIGHT));
            t.addCell(celda("S/ " + dinero(m.monto()), Element.ALIGN_RIGHT));
            t.addCell(celda(porcentaje(m.monto(), total), Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    private void seccionProductos(Document doc, List<ProductoVendido> lista) throws DocumentException {
        doc.add(tituloSeccion("3. Productos más vendidos"));
        if (lista.isEmpty()) {
            doc.add(sinDatos("Sin ventas confirmadas en este periodo."));
            return;
        }
        PdfPTable t = tabla(new float[]{6f, 14f, 38f, 18f, 12f, 16f}, "#", "Código", "Producto", "Categoría", "Cantidad", "Monto");
        int i = 1;
        for (ProductoVendido p : lista) {
            t.addCell(celda(String.valueOf(i++), Element.ALIGN_LEFT));
            t.addCell(celda(p.codigo(), Element.ALIGN_LEFT));
            t.addCell(celda(p.producto(), Element.ALIGN_LEFT));
            t.addCell(celda(p.categoria(), Element.ALIGN_LEFT));
            t.addCell(celda(cantidad(p.cantidad()) + " " + p.unidad(), Element.ALIGN_RIGHT));
            t.addCell(celda("S/ " + dinero(p.monto()), Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    private void seccionVendedores(Document doc, List<VentaPorVendedor> lista) throws DocumentException {
        doc.add(tituloSeccion("4. Ventas por vendedor"));
        if (lista.isEmpty()) {
            doc.add(sinDatos("Sin ventas confirmadas en este periodo."));
            return;
        }
        PdfPTable t = tabla(new float[]{45f, 20f, 15f, 20f}, "Vendedor", "Usuario", "Ventas", "Monto");
        for (VentaPorVendedor v : lista) {
            t.addCell(celda(v.vendedor(), Element.ALIGN_LEFT));
            t.addCell(celda(v.cuenta(), Element.ALIGN_LEFT));
            t.addCell(celda(String.valueOf(v.ventas()), Element.ALIGN_RIGHT));
            t.addCell(celda("S/ " + dinero(v.monto()), Element.ALIGN_RIGHT));
        }
        doc.add(t);
    }

    // ------------------------------------------------------------------ piezas

    private Paragraph tituloSeccion(String texto) {
        Paragraph p = new Paragraph(texto, SECCION);
        p.setSpacingBefore(10f);
        p.setSpacingAfter(6f);
        return p;
    }

    private Paragraph sinDatos(String texto) {
        Paragraph p = new Paragraph(texto, PEQUENA);
        p.setSpacingAfter(4f);
        return p;
    }

    private void tarjeta(PdfPTable t, String etiqueta, String valor) {
        PdfPCell c = new PdfPCell();
        c.setPadding(6f);
        c.setBorder(Rectangle.TOP);
        c.setBorderWidthTop(3f);
        c.setBorderColorTop(NARANJA);
        c.setBackgroundColor(new Color(255, 248, 235));
        c.addElement(new Paragraph(etiqueta.toUpperCase(Locale.ROOT), PEQUENA));
        c.addElement(new Paragraph(valor, CIFRA));
        t.addCell(c);
    }

    private PdfPTable tabla(float[] anchos, String... titulos) {
        PdfPTable t = new PdfPTable(anchos);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        t.setSpacingAfter(4f);
        for (int i = 0; i < titulos.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(titulos[i], CABECERA));
            c.setBackgroundColor(FONDO_CABECERA);
            c.setBorderColor(FONDO_CABECERA);
            c.setPadding(5f);
            // Las columnas de números van a la derecha, igual que sus celdas
            c.setHorizontalAlignment(NUMERICAS.contains(titulos[i]) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            t.addCell(c);
        }
        return t;
    }

    private PdfPCell celda(String texto, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto == null ? "" : texto, NORMAL));
        c.setHorizontalAlignment(alineacion);
        c.setPadding(4.5f);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(LINEA);
        return c;
    }

    private Paragraph centrado(String texto, Font fuente, float espacioAntes) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(espacioAntes);
        p.setLeading(fuente.getSize() + 3f);
        return p;
    }

    private String dinero(BigDecimal n) {
        return String.format(Locale.US, "%,.2f", n == null ? BigDecimal.ZERO : n);
    }

    private String cantidad(BigDecimal n) {
        return n == null ? "0" : n.stripTrailingZeros().toPlainString();
    }

    private String porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0 || parte == null) {
            return "0.0 %";
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP) + " %";
    }
}
