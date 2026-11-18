package ec.senadi;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;

public class PoaExcelReader {

    public ExcelReader.Tabla leer(InputStream input) throws Exception {

        try (Workbook libro = WorkbookFactory.create(input)) {

            DataFormatter formato = new DataFormatter(
                Locale.forLanguageTag("es-EC")
            );

            formato.setUseCachedValuesForFormulaCells(true);

            Sheet hoja = null;
            int columnaCodigo = -1;
            int columnaActividad = -1;

            // Los encabezados del POA están en la fila 3.
            for (Sheet candidata : libro) {

                Row encabezado = candidata.getRow(2);
                if (encabezado == null) {
                    continue;
                }

                int codigoEncontrado = -1;
                int actividadEncontrada = -1;

                for (Cell celda : encabezado) {

                    String nombre = normalizar(
                        formato.formatCellValue(celda)
                    );

                    if (nombre.equals("CODIGO UNICO INTERNO")) {
                        codigoEncontrado = celda.getColumnIndex();
                    }

                    if (nombre.equals("ACTIVIDAD POA")) {
                        actividadEncontrada = celda.getColumnIndex();
                    }
                }

                if (codigoEncontrado >= 0
                        && actividadEncontrada >= 0) {
                    if (hoja != null) {
                        throw new IllegalArgumentException(
                            "El archivo contiene varias hojas POA. "
                            + "Deja una sola matriz POA para importar."
                        );
                    }

                    hoja = candidata;
                    columnaCodigo = codigoEncontrado;
                    columnaActividad = actividadEncontrada;
                }
            }

            if (hoja == null) {
                throw new IllegalArgumentException(
                    "No se encontró la matriz POA. "
                    + "La fila 3 debe contener los encabezados "
                    + "Actividad POA y Código único interno."
                );
            }

            Row encabezado = hoja.getRow(2);

            if (encabezado.getLastCellNum() > 100
                    || hoja.getLastRowNum() > 10002) {
                throw new IllegalArgumentException(
                    "La matriz admite hasta 100 columnas "
                    + "y 10 000 filas de datos."
                );
            }

            if (hoja.isColumnHidden(columnaCodigo)
                    || hoja.isColumnHidden(columnaActividad)) {
                throw new IllegalArgumentException(
                    "Mantén visibles las columnas "
                    + "Código único interno y Actividad POA."
                );
            }

            List<Integer> columnasVisibles = new ArrayList<>();
            List<String> encabezados = new ArrayList<>();
            Set<String> nombresUtilizados = new HashSet<>();

            // Solo selecciona columnas visibles con encabezado.
            for (int columna = 0;
                    columna < encabezado.getLastCellNum();
                    columna++) {

                if (hoja.isColumnHidden(columna)) {
                    continue;
                }

                String nombre = texto(
                    encabezado.getCell(columna),
                    formato
                ).replaceAll("\\s+", " ").strip();

                if (nombre.isEmpty()) {
                    continue;
                }

                if (!nombresUtilizados.add(normalizar(nombre))) {
                    throw new IllegalArgumentException(
                        "Hay un encabezado visible duplicado: " + nombre
                    );
                }

                columnasVisibles.add(columna);
                encabezados.add(nombre);
            }

            // El ítem presupuestario se usará para el cruce con eSIGEF.
            if (!nombresUtilizados.contains("ITEM PRESUPUESTARIO")) {
                throw new IllegalArgumentException(
                    "La fila 3 debe incluir la columna visible "
                    + "Ítem presupuestario."
                );
            }

            // Permite las combinaciones de las filas de título.
            // Evita importar datos ambiguos en celdas combinadas.
            for (var region : hoja.getMergedRegions()) {

                boolean afectaColumnasImportadas =
                    columnasVisibles.stream().anyMatch(
                        columna ->
                            columna >= region.getFirstColumn()
                            && columna <= region.getLastColumn()
                    );

                if (region.getLastRow() >= 3
                        && afectaColumnasImportadas) {
                    throw new IllegalArgumentException(
                        "Hay celdas combinadas dentro de los datos: "
                        + region.formatAsString()
                        + ". Las combinaciones solo se permiten "
                        + "en las filas de título."
                    );
                }
            }

            List<ExcelReader.Fila> filas = new ArrayList<>();
            Set<String> codigosUtilizados = new HashSet<>();

            // Los datos comienzan en la fila 4.
            for (int numeroFila = 3;
                    numeroFila <= hoja.getLastRowNum();
                    numeroFila++) {

                Row fila = hoja.getRow(numeroFila);
                if (fila == null) {
                    continue;
                }

                String codigo = texto(
                    fila.getCell(columnaCodigo),
                    formato
                ).strip();

                if (codigo.isEmpty()) {

                    String actividad = texto(
                        fila.getCell(columnaActividad),
                        formato
                    );

                    if (!actividad.isBlank()) {
                        throw new IllegalArgumentException(
                            "La actividad de la fila "
                            + (numeroFila + 1)
                            + " no tiene código único interno."
                        );
                    }

                    // Omite notas, totales y filas sin actividad ni código.
                    continue;
                }

                if (!codigosUtilizados.add(normalizar(codigo))) {
                    throw new IllegalArgumentException(
                        "Código único interno duplicado: "
                        + codigo
                        + ", en la fila "
                        + (numeroFila + 1)
                        + "."
                    );
                }

                List<String> valores = new ArrayList<>();

                for (int columna : columnasVisibles) {
                    valores.add(
                        texto(fila.getCell(columna), formato)
                    );
                }

                int posicionCodigo = columnasVisibles.indexOf(
                    columnaCodigo
                );
                valores.set(posicionCodigo, codigo);

                filas.add(
                    new ExcelReader.Fila(
                        numeroFila + 1,
                        valores
                    )
                );
            }

            if (filas.isEmpty()) {
                throw new IllegalArgumentException(
                    "La matriz POA no contiene actividades "
                    + "con código único interno."
                );
            }

            return new ExcelReader.Tabla(
                hoja.getSheetName(),
                encabezados,
                filas
            );
        }
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(
            valor,
            Normalizer.Form.NFD
        )
        .replaceAll("\\p{M}", "")
        .replaceAll("\\s+", " ")
        .strip()
        .toUpperCase(Locale.ROOT);
    }

    private String texto(
            Cell celda,
            DataFormatter formato
    ) {

        if (celda == null) {
            return "";
        }

        CellType tipo = celda.getCellType();

        if (tipo == CellType.FORMULA) {

            // Usa el resultado guardado por Excel.
            // No abre ni recalcula libros externos.
            if (celda instanceof XSSFCell celdaXlsx) {

                boolean faltaResultado =
                    !celdaXlsx.getCTCell().isSetV();

                boolean numeroSinResultado =
                    celda.getCachedFormulaResultType()
                        == CellType.NUMERIC
                    && (
                        celdaXlsx.getCTCell().getV() == null
                        || celdaXlsx.getCTCell().getV().isBlank()
                    );

                if (faltaResultado || numeroSinResultado) {
                    throw new IllegalArgumentException(
                        "La fórmula de la celda "
                        + celda.getAddress()
                        + " no tiene resultado guardado. "
                        + "Recalcula y guarda el archivo en Excel "
                        + "antes de importarlo."
                    );
                }
            }

            tipo = celda.getCachedFormulaResultType();
        }

        if (tipo == CellType.ERROR
                || tipo == CellType._NONE) {
            throw new IllegalArgumentException(
                "La celda "
                + celda.getAddress()
                + " contiene un error. "
                + "Corrígelo en Excel antes de importar."
            );
        }

        String valor = formato.formatCellValue(celda);
        if (valor.length() > 10000) {
            throw new IllegalArgumentException(
                "La celda "
                + celda.getAddress()
                + " supera los 10 000 caracteres."
            );
        }

        return valor;
    }
}