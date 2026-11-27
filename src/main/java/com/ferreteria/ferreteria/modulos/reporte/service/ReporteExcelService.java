package com.ferreteria.ferreteria.modulos.reporte.service;

import com.ferreteria.ferreteria.modulos.reporte.dto.ProductoVendido;
import com.ferreteria.ferreteria.modulos.reporte.dto.RangoReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.ResumenReporte;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorMetodo;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaPorVendedor;
import com.ferreteria.ferreteria.modulos.reporte.dto.VentaRegistro;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reporte de ventas en Excel (.xlsx), una hoja por tema: resumen, ventas del periodo,
 * medios de pago, más vendidos y vendedores.
 * Un .xlsx es un zip con archivos XML, así que se arma con java.util.zip, sin librerías nuevas.
 * Los montos van como números (se pueden sumar en Excel) y la hoja de ventas trae filtro por columna.
 */
@Service
public class ReporteExcelService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final LocalDateTime ORIGEN_EXCEL = LocalDateTime.of(1899, 12, 30, 0, 0);

    // Estilos definidos en styles.xml (posición en cellXfs)
    private static final int TEXTO = 0;
    private static final int CABECERA = 1;
    private static final int DINERO = 2;
    private static final int FECHA_HORA = 3;
    private static final int NEGRITA = 4;
    private static final int TITULO = 5;
    private static final int DINERO_NEGRITA = 6;

    /** Una hoja: nombre, anchos de columna, filas y, si tiene, el rango del filtro. */
    private record Hoja(String nombre, int[] anchos, List<String> filas, String filtro) {}

    public byte[] generar(RangoReporte rango, ResumenReporte resumen, List<VentaRegistro> ventas,
                          List<VentaPorMetodo> metodos, List<ProductoVendido> productos,
                          List<VentaPorVendedor> vendedores) {
        String periodo = "Del " + rango.desde().format(FECHA) + " al " + rango.hasta().format(FECHA);
        List<Hoja> hojas = List.of(
                hojaResumen(periodo, resumen),
                hojaVentas(periodo, ventas),
                hojaMetodos(periodo, metodos),
                hojaProductos(periodo, productos),
                hojaVendedores(periodo, vendedores));

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            agregar(zip, "[Content_Types].xml", tiposDeContenido(hojas.size()));
            agregar(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            agregar(zip, "xl/workbook.xml", libro(hojas));
            agregar(zip, "xl/_rels/workbook.xml.rels", relacionesLibro(hojas.size()));
            agregar(zip, "xl/styles.xml", ESTILOS);
            for (int i = 0; i < hojas.size(); i++) {
                agregar(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", hoja(hojas.get(i)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel del reporte", e);
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------ hojas

    private Hoja hojaResumen(String periodo, ResumenReporte r) {
        List<String> filas = encabezado("Reporte de ventas", periodo);
        filas.add(fila(4, texto("A", 4, "Dato", CABECERA), texto("B", 4, "Valor", CABECERA)));
        filas.add(fila(5, texto("A", 5, "Total vendido (S/)", NEGRITA), numero("B", 5, r.totalVendido(), DINERO_NEGRITA)));
        filas.add(fila(6, texto("A", 6, "Ventas confirmadas", NEGRITA), numero("B", 6, BigDecimal.valueOf(r.numVentas()), TEXTO)));
        filas.add(fila(7, texto("A", 7, "Ventas anuladas", NEGRITA), numero("B", 7, BigDecimal.valueOf(r.numAnuladas()), TEXTO)));
        filas.add(fila(8, texto("A", 8, "Monto anulado (S/)", NEGRITA), numero("B", 8, r.montoAnuladas(), DINERO)));
        filas.add(fila(10, texto("A", 10, "Lo vendido solo cuenta ventas confirmadas. Las anuladas devolvieron su stock.", TEXTO)));
        return new Hoja("Resumen", new int[]{24, 18}, filas, null);
    }

    private Hoja hojaVentas(String periodo, List<VentaRegistro> ventas) {
        List<String> filas = encabezado("Ventas del periodo", periodo);
        filas.add(cabeceras(4, "N.º venta", "Fecha y hora", "Comprobante", "Cliente", "Vendedor", "Medio de pago", "Total (S/)", "Estado"));
        int f = 5;
        for (VentaRegistro v : ventas) {
            filas.add(fila(f, texto("A", f, v.numero(), TEXTO), fecha("B", f, v.fecha()),
                    texto("C", f, v.comprobante(), TEXTO), texto("D", f, v.cliente(), TEXTO),
                    texto("E", f, v.vendedor(), TEXTO), texto("F", f, v.metodo(), TEXTO),
                    numero("G", f, v.total(), DINERO), texto("H", f, v.estado(), TEXTO)));
            f++;
        }
        // Filtro de Excel en la cabecera: permite ver solo las confirmadas o las anuladas
        String filtro = ventas.isEmpty() ? null : "A4:H" + (f - 1);
        return new Hoja("Ventas", new int[]{12, 18, 16, 32, 26, 16, 13, 13}, filas, filtro);
    }

    private Hoja hojaMetodos(String periodo, List<VentaPorMetodo> metodos) {
        List<String> filas = encabezado("Cobrado por medio de pago", periodo);
        filas.add(cabeceras(4, "Medio de pago", "Ventas", "Monto (S/)"));
        int f = 5;
        for (VentaPorMetodo m : metodos) {
            filas.add(fila(f, texto("A", f, m.metodo(), TEXTO), numero("B", f, BigDecimal.valueOf(m.ventas()), TEXTO),
                    numero("C", f, m.monto(), DINERO)));
            f++;
        }
        return new Hoja("Medios de pago", new int[]{22, 10, 14}, filas, null);
    }

    private Hoja hojaProductos(String periodo, List<ProductoVendido> productos) {
        List<String> filas = encabezado("Productos más vendidos", periodo);
        filas.add(cabeceras(4, "#", "Código", "Producto", "Categoría", "Cantidad", "Unidad", "Monto (S/)"));
        int f = 5;
        for (ProductoVendido p : productos) {
            filas.add(fila(f, numero("A", f, BigDecimal.valueOf(f - 4), TEXTO), texto("B", f, p.codigo(), TEXTO),
                    texto("C", f, p.producto(), TEXTO), texto("D", f, p.categoria(), TEXTO),
                    numero("E", f, p.cantidad(), TEXTO), texto("F", f, p.unidad(), TEXTO),
                    numero("G", f, p.monto(), DINERO)));
            f++;
        }
        return new Hoja("Más vendidos", new int[]{5, 12, 36, 20, 10, 8, 14}, filas, null);
    }

    private Hoja hojaVendedores(String periodo, List<VentaPorVendedor> vendedores) {
        List<String> filas = encabezado("Ventas por vendedor", periodo);
        filas.add(cabeceras(4, "Vendedor", "Usuario", "Ventas", "Monto (S/)"));
        int f = 5;
        for (VentaPorVendedor v : vendedores) {
            filas.add(fila(f, texto("A", f, v.vendedor(), TEXTO), texto("B", f, v.cuenta(), TEXTO),
                    numero("C", f, BigDecimal.valueOf(v.ventas()), TEXTO), numero("D", f, v.monto(), DINERO)));
            f++;
        }
        return new Hoja("Vendedores", new int[]{30, 18, 10, 14}, filas, null);
    }

    // ------------------------------------------------------------------ celdas y filas

    private List<String> encabezado(String titulo, String periodo) {
        List<String> filas = new ArrayList<>();
        filas.add(fila(1, texto("A", 1, "LA SOLUCIÓN FERRETERA - " + titulo, TITULO)));
        filas.add(fila(2, texto("A", 2, periodo, TEXTO)));
        return filas;
    }

    private String cabeceras(int f, String... titulos) {
        String[] celdas = new String[titulos.length];
        for (int i = 0; i < titulos.length; i++) {
            celdas[i] = texto(String.valueOf((char) ('A' + i)), f, titulos[i], CABECERA);
        }
        return fila(f, celdas);
    }

    private String fila(int f, String... celdas) {
        return "<row r=\"" + f + "\">" + String.join("", celdas) + "</row>";
    }

    private String texto(String col, int f, String valor, int estilo) {
        return "<c r=\"" + col + f + "\" t=\"inlineStr\" s=\"" + estilo + "\"><is><t xml:space=\"preserve\">"
                + xml(valor) + "</t></is></c>";
    }

    private String numero(String col, int f, BigDecimal valor, int estilo) {
        BigDecimal n = valor == null ? BigDecimal.ZERO : valor;
        return "<c r=\"" + col + f + "\" s=\"" + estilo + "\"><v>" + n.toPlainString() + "</v></c>";
    }

    // Excel guarda la fecha como días desde 30/12/1899; la hora es la fracción del día
    private String fecha(String col, int f, LocalDateTime valor) {
        if (valor == null) {
            return texto(col, f, "", TEXTO);
        }
        double serial = ChronoUnit.SECONDS.between(ORIGEN_EXCEL, valor) / 86400.0;
        return "<c r=\"" + col + f + "\" s=\"" + FECHA_HORA + "\"><v>" + serial + "</v></c>";
    }

    private String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ partes del archivo

    private String hoja(Hoja h) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""");
        // La cabecera de la tabla queda fija al bajar
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"4\" topLeftCell=\"A5\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        sb.append("<cols>");
        for (int i = 0; i < h.anchos().length; i++) {
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(h.anchos()[i]).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        h.filas().forEach(sb::append);
        sb.append("</sheetData>");
        if (h.filtro() != null) {
            sb.append("<autoFilter ref=\"").append(h.filtro()).append("\"/>");
        }
        sb.append("</worksheet>");
        return sb.toString();
    }

    private String libro(List<Hoja> hojas) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""");
        for (int i = 0; i < hojas.size(); i++) {
            sb.append("<sheet name=\"").append(xml(hojas.get(i).nombre())).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets>");
        // Excel pide este nombre oculto para cada hoja con filtro
        StringBuilder nombres = new StringBuilder();
        for (int i = 0; i < hojas.size(); i++) {
            Hoja h = hojas.get(i);
            if (h.filtro() != null) {
                String[] partes = h.filtro().split(":");
                nombres.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i)
                        .append("\" hidden=\"1\">'").append(xml(h.nombre())).append("'!$")
                        .append(partes[0].replaceAll("(\\D+)(\\d+)", "$1\\$$2")).append(":$")
                        .append(partes[1].replaceAll("(\\D+)(\\d+)", "$1\\$$2")).append("</definedName>");
            }
        }
        if (!nombres.isEmpty()) {
            sb.append("<definedNames>").append(nombres).append("</definedNames>");
        }
        sb.append("</workbook>");
        return sb.toString();
    }

    private String relacionesLibro(int numHojas) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""");
        for (int i = 1; i <= numHojas; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(numHojas + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        sb.append("</Relationships>");
        return sb.toString();
    }

    private String tiposDeContenido(int numHojas) {
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""");
        for (int i = 1; i <= numHojas; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private void agregar(ZipOutputStream zip, String ruta, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(ruta));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // Cabecera negra con letra blanca, como las tablas de la pantalla y del PDF
    private static final String ESTILOS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <numFmts count="2">
            <numFmt numFmtId="164" formatCode="#,##0.00"/>
            <numFmt numFmtId="165" formatCode="dd/mm/yyyy hh:mm"/>
            </numFmts>
            <fonts count="4">
            <font><sz val="11"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
            <font><b/><sz val="14"/><name val="Calibri"/></font>
            </fonts>
            <fills count="3">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF1F1F1F"/><bgColor indexed="64"/></patternFill></fill>
            </fills>
            <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
            <cellXfs count="7">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
            <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
            <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>
            </cellXfs>
            <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>""";
}
