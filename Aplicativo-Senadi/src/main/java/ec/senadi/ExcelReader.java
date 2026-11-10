package ec.senadi;

import java.io.InputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

@Component
public class ExcelReader {
    public record Fila(int numero, List<String> valores) {}
    public record Tabla(String hoja, List<String> encabezados, List<Fila> filas) {}
    public Tabla leer(InputStream input) throws Exception {
        try (Workbook libro = WorkbookFactory.create(input)) {
            if (libro.getNumberOfSheets() == 0) throw new IllegalArgumentException("El archivo no tiene hojas.");
            Sheet hoja = libro.getSheetAt(0);
            if (hoja.getNumMergedRegions() > 0) throw new IllegalArgumentException("Separa las celdas combinadas antes de importar.");
            Row cabecera = hoja.getRow(0);
            if (cabecera == null || cabecera.getLastCellNum() < 1) throw new IllegalArgumentException("La primera fila debe contener los encabezados.");
            int columnas = cabecera.getLastCellNum();
            if (columnas > 100 || hoja.getLastRowNum() > 10000) throw new IllegalArgumentException("Máximo 100 columnas y 10 000 filas de datos.");
            DataFormatter formato = new DataFormatter(Locale.forLanguageTag("es-EC"));
            List<String> encabezados = new ArrayList<>();
            Set<String> nombres = new HashSet<>();
            for (int c = 0; c < columnas; c++) {
                String valor = texto(cabecera.getCell(c), formato).strip();
                if (valor.isBlank() || !nombres.add(valor.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Los encabezados deben ser únicos y no estar vacíos.");
                encabezados.add(valor);
            }
            List<Fila> filas = new ArrayList<>();
            for (int r = 1; r <= hoja.getLastRowNum(); r++) {
                Row fila = hoja.getRow(r);
                if (fila == null) continue;
                for (int c = columnas; c < fila.getLastCellNum(); c++) {
                    if (!texto(fila.getCell(c), formato).isBlank()) throw new IllegalArgumentException("Hay datos sin encabezado en la fila " + (r+1) + ".");
                }
                List<String> valores = new ArrayList<>();
                for (int c = 0; c < columnas; c++) valores.add(texto(fila.getCell(c), formato));
                if (valores.stream().anyMatch(v -> !v.isBlank())) filas.add(new Fila(r + 1, valores));
            }
            if (filas.isEmpty()) throw new IllegalArgumentException("No hay filas de datos para importar.");
            return new Tabla(hoja.getSheetName(), encabezados, filas);
        }
    }
    private String texto(Cell celda, DataFormatter formato) {
        if (celda == null) return "";
        if (celda.getCellType() == CellType.FORMULA) throw new IllegalArgumentException("Convierte las fórmulas a valores antes de importar (fila " + (celda.getRowIndex()+1) + ").");
        if (celda.getCellType() == CellType.ERROR) throw new IllegalArgumentException("Hay una celda con error en el Excel.");
        String valor = formato.formatCellValue(celda);
        if (valor.length() > 10000) throw new IllegalArgumentException("Una celda supera los 10 000 caracteres.");
        return valor;
    }
}
