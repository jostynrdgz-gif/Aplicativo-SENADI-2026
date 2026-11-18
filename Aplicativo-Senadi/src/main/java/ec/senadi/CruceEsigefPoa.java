package ec.senadi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CruceEsigefPoa {

    private CruceEsigefPoa() {}

    static ExcelReader.Tabla validar(
            ExcelReader.Tabla reporte, Long idPoa,
            List<String> encabezadosPoa, List<List<String>> filasPoa
    ) {
        Map<String, String> itemsPorCodigo = new HashMap<>();
        if (idPoa != null) {
            int codigoPoa = columna(encabezadosPoa, "CODIGO UNICO INTERNO");
            int itemPoa = columna(encabezadosPoa, "ITEM PRESUPUESTARIO");
            if (codigoPoa < 0 || itemPoa < 0) {
                throw new IllegalArgumentException(
                        "La POA vigente debe tener Código único interno e Ítem presupuestario.");
            }
            for (List<String> fila : filasPoa) {
                String codigo = valor(fila, codigoPoa).toUpperCase(Locale.ROOT);
                if (!codigo.isEmpty()
                        && itemsPorCodigo.putIfAbsent(codigo, valor(fila, itemPoa)) != null) {
                    throw new IllegalArgumentException("Código duplicado en la POA vigente: " + codigo);
                }
            }
        }

        List<String> encabezados = new ArrayList<>(reporte.encabezados());
        int descripcion = columna(encabezados, "DESCRIPCION");
        int renglon = columna(encabezados, "RENGLON", "ITEM PRESUPUESTARIO");
        int codigoUnico = encabezados.size() - 1; // Columna añadida por el lector.
        encabezados.addAll(List.of("codigo_candidato", "estado_cruce", "poa_importacion_id"));

        List<ExcelReader.Fila> filas = new ArrayList<>();
        for (var fila : reporte.filas()) {
            List<String> valores = new ArrayList<>(fila.valores());
            String candidato = valor(valores, codigoUnico);
            String texto = valor(valores, descripcion);
            String estado;
            if (candidato.isEmpty()) {
                estado = !texto.startsWith("=")
                        && EsigefExcelReader.codigosEnDescripcion(texto).size() > 1
                        ? "VARIOS_CODIGOS" : "SIN_CODIGO";
            } else if (idPoa == null) {
                estado = "SIN_POA";
            } else {
                estado = compararItem(itemsPorCodigo.get(candidato), valor(valores, renglon));
            }

            valores.set(codigoUnico, estado.equals("VALIDADO") ? candidato : "");
            valores.add(candidato);
            valores.add(estado);
            valores.add(idPoa == null ? "" : idPoa.toString());
            filas.add(new ExcelReader.Fila(fila.numero(), valores));
        }
        return new ExcelReader.Tabla(reporte.hoja(), encabezados, filas);
    }

    private static String compararItem(String itemPoa, String renglon) {
        if (itemPoa == null) return "CODIGO_NO_EXISTE";
        if (itemPoa.isEmpty()) return "POA_SIN_ITEM";
        if (renglon.isEmpty()) return "SIN_RENGLON";
        return itemPoa.equals(renglon) ? "VALIDADO" : "ITEM_NO_COINCIDE";
    }

    private static int columna(List<String> encabezados, String... nombres) {
        for (int i = 0; i < encabezados.size(); i++) {
            String encabezado = EsigefExcelReader.normalizarEncabezado(encabezados.get(i));
            for (String nombre : nombres) {
                if (nombre.equals(encabezado)) return i;
            }
        }
        return -1;
    }

    private static String valor(List<String> valores, int indice) {
        return indice >= 0 && indice < valores.size() && valores.get(indice) != null
                ? valores.get(indice).strip() : "";
    }
}
