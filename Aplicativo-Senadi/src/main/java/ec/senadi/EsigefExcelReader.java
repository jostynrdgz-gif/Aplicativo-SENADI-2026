package ec.senadi;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;

public class EsigefExcelReader {

    private static final Set<String> REQUERIDOS =
            Set.of("DESCRIPCION", "ESTADO", "RENGLON");

    private static final Pattern CODIGO_POA = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])[A-Z][A-Z0-9]*_[0-9]+(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE);

    public ExcelReader.Tabla leer(InputStream input) throws Exception {
        try (var libro = WorkbookFactory.create(input)) {
            var formato = new DataFormatter(Locale.forLanguageTag("es-EC"));
            Sheet hoja = null;

            for (Sheet candidata : libro) {
                if (tieneEncabezados(candidata.getRow(0), formato)) {
                    if (hoja != null) {
                        throw new IllegalArgumentException(
                                "El archivo contiene más de una hoja eSIGEF.");
                    }
                    hoja = candidata;
                }
            }

            if (hoja == null) {
                throw new IllegalArgumentException(
                        "La fila 1 del eSIGEF debe incluir DESCRIPCION, ESTADO y RENGLON.");
            }

            if (hoja.getLastRowNum() > 10000) {
                throw new IllegalArgumentException("El reporte admite hasta 10 000 filas de datos.");
            }

            int columnas = 0;
            for (Row fila : hoja) {
                columnas = Math.max(columnas, fila.getLastCellNum());
            }
            if (columnas > 100) {
                throw new IllegalArgumentException("El reporte admite hasta 100 columnas.");
            }

            List<String> encabezados = new ArrayList<>();
            List<ExcelReader.Fila> filas = new ArrayList<>();
            int columnaDescripcion = -1;
            for (Row fila : hoja) {
                List<String> valores = new ArrayList<>(columnas + 1);
                for (int columna = 0; columna < columnas; columna++) {
                    valores.add(leerCelda(fila.getCell(columna), formato));
                }
                if (fila.getRowNum() == 0) {
                    encabezados = valores;
                    for (int columna = 0; columna < columnas; columna++) {
                        if (normalizarEncabezado(encabezados.get(columna)).equals("DESCRIPCION")) {
                            columnaDescripcion = columna;
                            break;
                        }
                    }
                    encabezados.add("codigo_unico");
                } else if (valores.stream().anyMatch(valor -> !valor.isBlank())) {
                    Cell descripcion = fila.getCell(columnaDescripcion);
                    valores.add(descripcion != null && descripcion.getCellType() == CellType.STRING
                            ? extraerCodigoUnico(valores.get(columnaDescripcion))
                            : "");
                    filas.add(new ExcelReader.Fila(fila.getRowNum() + 1, valores));
                }
            }
            if (filas.isEmpty()) {
                throw new IllegalArgumentException("El reporte eSIGEF no contiene filas de datos.");
            }
            return new ExcelReader.Tabla(hoja.getSheetName(), encabezados, filas);
        }
    }

    private boolean tieneEncabezados(Row fila, DataFormatter formato) {
        if (fila == null) {
            return false;
        }
        Set<String> encontrados = new HashSet<>();
        boolean duplicado = false;
        for (Cell celda : fila) {
            String nombre = normalizarEncabezado(leerCelda(celda, formato));
            if (nombre.equals("ITEM PRESUPUESTARIO")) {
                nombre = "RENGLON";
            }
            if (REQUERIDOS.contains(nombre) && !encontrados.add(nombre)) {
                duplicado = true;
            }
        }
        if (!encontrados.containsAll(REQUERIDOS)) {
            return false;
        }
        if (duplicado) {
            throw new IllegalArgumentException("El reporte eSIGEF tiene encabezados requeridos duplicados.");
        }
        return true;
    }

    static String normalizarEncabezado(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[_\\s]+", " ")
                .strip().toUpperCase(Locale.ROOT);
    }

    private static String extraerCodigoUnico(String descripcion) {
        Set<String> codigos = codigosEnDescripcion(descripcion);
        return codigos.size() == 1 ? codigos.iterator().next() : "";
    }

    static Set<String> codigosEnDescripcion(String descripcion) {
        Set<String> codigos = new HashSet<>();
        var coincidencias = CODIGO_POA.matcher(descripcion);
        while (coincidencias.find()) {
            codigos.add(coincidencias.group().toUpperCase(Locale.ROOT));
        }
        return codigos;
    }

    private String leerCelda(Cell celda, DataFormatter formato) {
        if (celda == null) {
            return "";
        }
        return switch (celda.getCellType()) {
            case FORMULA -> "=" + celda.getCellFormula();
            case NUMERIC -> DateUtil.isCellDateFormatted(celda)
                    ? formato.formatCellValue(celda)
                    : NumberToTextConverter.toText(celda.getNumericCellValue());
            default -> formato.formatCellValue(celda);
        };
    }
}
