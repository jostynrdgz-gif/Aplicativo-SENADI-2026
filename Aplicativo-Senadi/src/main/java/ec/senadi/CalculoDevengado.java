package ec.senadi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class CalculoDevengado {

    private static final Pattern IMPORTE = Pattern.compile("[+-]?\\d+(?:[.,]\\d{1,2})?");

    private static final List<DateTimeFormatter> FECHAS_APROBACION = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M/d/uu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M-d-uu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT)
    );

    public record Clave(String itemPresupuestario, String codigoUnico) {}

    private CalculoDevengado() {}

    public static Map<Clave, BigDecimal> sumar(
            ExcelReader.Tabla reporte, long idPoaVigente, String columnaImporte
    ) {
        return sumar(reporte, idPoaVigente, columnaImporte, null);
    }

    public static Map<Clave, BigDecimal> sumar(
            ExcelReader.Tabla reporte, long idPoaVigente, String columnaImporte, LocalDate corte
    ) {
        List<String> encabezados = reporte.encabezados();
        int estado = columna(encabezados, "ESTADO");
        int cruce = columna(encabezados, "ESTADO CRUCE");
        int codigo = columna(encabezados, "CODIGO UNICO");
        int renglon = columna(encabezados, "RENGLON", "ITEM PRESUPUESTARIO");
        int poa = columna(encabezados, "POA IMPORTACION ID");
        int fechaAprobacion = corte == null ? -1 : columna(encabezados, "FEC APROBADO");
        String nombreImporte = EsigefExcelReader.normalizarEncabezado(columnaImporte);
        if (!nombreImporte.startsWith("MONTO ") && !nombreImporte.equals("DEVENGADO")
                && !nombreImporte.startsWith("DEVENGADO ")) {
            throw new IllegalArgumentException("Selecciona un campo de importe del eSIGEF.");
        }
        int importe = columna(encabezados, nombreImporte);

        Map<Clave, BigDecimal> totales = new LinkedHashMap<>();
        String poaEsperada = Long.toString(idPoaVigente);
        for (var fila : reporte.filas()) {
            List<String> valores = fila.valores();
            if (!poaEsperada.equals(valor(valores, poa))) {
                throw new IllegalArgumentException(
                        "Vuelve a importar el reporte para validarlo con la POA vigente.");
            }
            if (!"APROBADO".equalsIgnoreCase(valor(valores, estado))
                    || !"VALIDADO".equals(valor(valores, cruce))) {
                continue;
            }
            if (corte != null && fecha(valor(valores, fechaAprobacion), fila.numero()).isAfter(corte)) {
                continue;
            }
            String codigoUnico = valor(valores, codigo);
            String item = valor(valores, renglon);
            if (codigoUnico.isEmpty() || item.isEmpty()) {
                throw new IllegalArgumentException(
                        "La fila " + fila.numero() + " no tiene un código y renglón validados.");
            }
            String texto = valor(valores, importe);
            if (!IMPORTE.matcher(texto).matches()) {
                throw new IllegalArgumentException(
                        "Importe vacío o inválido en la fila " + fila.numero() + ".");
            }
            BigDecimal monto = new BigDecimal(texto.replace(',', '.')).setScale(2);
            totales.merge(new Clave(item, codigoUnico), monto, BigDecimal::add);
        }
        return Map.copyOf(totales);
    }

    private static LocalDate fecha(String texto, int fila) {
        for (var formato : FECHAS_APROBACION) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException e) {
                // Prueba el siguiente formato del reporte.
            }
        }
        throw new IllegalArgumentException("Fecha de aprobación vacía o inválida en la fila " + fila + ".");
    }

    private static int columna(List<String> encabezados, String... nombres) {
        int encontrada = -1;
        for (int i = 0; i < encabezados.size(); i++) {
            String encabezado = EsigefExcelReader.normalizarEncabezado(encabezados.get(i));
            if (List.of(nombres).contains(encabezado)) {
                if (encontrada >= 0) {
                    throw new IllegalArgumentException("Columna ambigua en el reporte: " + encabezado);
                }
                encontrada = i;
            }
        }
        if (encontrada < 0) {
            throw new IllegalArgumentException("Falta la columna " + nombres[0] + " en el reporte.");
        }
        return encontrada;
    }

    private static String valor(List<String> valores, int i) {
        return i < valores.size() && valores.get(i) != null ? valores.get(i).strip() : "";
    }
}
