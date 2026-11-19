package ec.senadi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportacionService {

    private final JdbcTemplate jdbc;
    private final ExcelReader reader;
    private final ObjectMapper json;

    public ImportacionService(
            JdbcTemplate jdbc,
            ExcelReader reader,
            ObjectMapper json
    ) {
        this.jdbc = jdbc;
        this.reader = reader;
        this.json = json;
    }

    // Importación genérica utilizada por las pruebas existentes.
    @Transactional(rollbackFor = Exception.class)
    public long importar(MultipartFile archivo) throws Exception {
        return guardar(archivo, "GENERICO", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public long importarPoa(
            MultipartFile archivo,
            String usuario
    ) throws Exception {
        return guardar(archivo, "POA", usuario);
    }

    @Transactional(rollbackFor = Exception.class)
    public long importarMatrizPoa(
            MultipartFile archivo,
            String usuario
    ) throws Exception {
        return guardar(archivo, "POA", usuario);
    }

    @Transactional(rollbackFor = Exception.class)
    public long importarEsigef(
            MultipartFile archivo,
            String usuario
    ) throws Exception {
        return guardar(archivo, "ESIGEF", usuario);
    }

    private long guardar(
            MultipartFile archivo,
            String tipo,
            String usuario
    ) throws Exception {

        String nombre = Optional.ofNullable(
            archivo.getOriginalFilename()
        ).orElse("").replace('\\', '/');

        nombre = nombre.substring(nombre.lastIndexOf('/') + 1);

        if (archivo.isEmpty()
                || archivo.getSize() > 10 * 1024 * 1024
                || nombre.length() > 255
                || !nombre.toLowerCase(Locale.ROOT)
                    .matches(".*\\.xlsx?")) {

            throw new IllegalArgumentException(
                "Selecciona un archivo .xls o .xlsx de hasta 10 MB."
            );
        }

        ExcelReader.Tabla tabla;

        try (var input = archivo.getInputStream()) {
            tabla = switch (tipo) {
                case "POA" -> new PoaExcelReader().leer(input);
                case "ESIGEF" -> validarEsigef(new EsigefExcelReader().leer(input), idPoaVigente());
                default -> reader.leer(input);
            };
        }

        String encabezados = json.writeValueAsString(
            tabla.encabezados()
        );

        var clave = new GeneratedKeyHolder();
        final String nombreArchivo = nombre;

        jdbc.update(conexion -> {

            PreparedStatement sentencia =
                conexion.prepareStatement(
                    """
                    INSERT INTO excel_importacion
                        (
                            archivo,
                            hoja,
                            encabezados,
                            filas,
                            tipo,
                            subido_por
                        )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    new String[]{"id"}
                );

            sentencia.setString(1, nombreArchivo);
            sentencia.setString(2, tabla.hoja());
            sentencia.setString(3, encabezados);
            sentencia.setInt(4, tabla.filas().size());
            sentencia.setString(5, tipo);
            sentencia.setString(6, usuario);

            return sentencia;

        }, clave);

        long id = Objects.requireNonNull(
            clave.getKey()
        ).longValue();

        List<Object[]> datos = new ArrayList<>();

        for (var fila : tabla.filas()) {

            datos.add(new Object[]{
                id,
                fila.numero(),
                json.writeValueAsString(fila.valores())
            });
        }

        jdbc.batchUpdate(
            """
            INSERT INTO excel_fila
                (importacion_id, numero_fila, valores)
            VALUES (?, ?, ?)
            """,
            datos
        );

        return id;
    }

    private ExcelReader.Tabla validarEsigef(ExcelReader.Tabla reporte, Long idPoa) throws Exception {
        if (idPoa == null) {
            return CruceEsigefPoa.validar(reporte, null, List.of(), List.of());
        }

        var tipoLista = new TypeReference<List<String>>() {};
        String encabezadosJson = jdbc.queryForObject(
                "SELECT encabezados FROM excel_importacion WHERE id = ?", String.class, idPoa);
        List<String> encabezados = json.readValue(encabezadosJson, tipoLista);
        List<List<String>> filas = new ArrayList<>();
        for (String valores : jdbc.queryForList(
                "SELECT valores FROM excel_fila WHERE importacion_id = ?", String.class, idPoa)) {
            filas.add(json.readValue(valores, tipoLista));
        }
        return CruceEsigefPoa.validar(reporte, idPoa, encabezados, filas);
    }

    @Transactional(readOnly = true)
    public Map<CalculoDevengado.Clave, java.math.BigDecimal> calcularDevengado(
            long idEsigef, String columnaImporte
    ) throws Exception {
        return calcularDevengado(idEsigef, columnaImporte, null);
    }

    @Transactional(readOnly = true)
    public Map<CalculoDevengado.Clave, java.math.BigDecimal> calcularDevengado(
            long idEsigef, String columnaImporte, java.time.LocalDate corte
    ) throws Exception {
        Long idPoa = idPoaVigente();
        if (idPoa == null) {
            throw new IllegalArgumentException("Publica una matriz POA antes de calcular el devengado.");
        }
        return CalculoDevengado.sumar(leerReporteCruzado(idEsigef, idPoa), idPoa, columnaImporte, corte);
    }

    private ExcelReader.Tabla leerReporteEsigef(long idEsigef) throws Exception {
        var reportes = jdbc.queryForList(
                "SELECT encabezados FROM excel_importacion WHERE id = ? AND tipo = 'ESIGEF'",
                String.class, idEsigef);
        if (reportes.isEmpty()) {
            throw new IllegalArgumentException("No existe ese reporte eSIGEF.");
        }
        var tipoLista = new TypeReference<List<String>>() {};
        List<String> encabezados = json.readValue(reportes.getFirst(), tipoLista);
        List<ExcelReader.Fila> filas = new ArrayList<>();
        var registros = jdbc.query(
                """
                SELECT numero_fila, valores FROM excel_fila
                WHERE importacion_id = ?
                ORDER BY numero_fila, id
                """,
                (rs, n) -> Map.entry(rs.getInt("numero_fila"), rs.getString("valores")),
                idEsigef
        );
        for (var registro : registros) {
            filas.add(new ExcelReader.Fila(registro.getKey(), json.readValue(registro.getValue(), tipoLista)));
        }
        return new ExcelReader.Tabla("eSIGEF", encabezados, filas);
    }

    private ExcelReader.Tabla leerReporteCruzado(long idReporte, Long idPoa) throws Exception {
        var reporte = leerReporteEsigef(idReporte);
        int indiceCodigo = CruceEsigefPoa.indiceCodigoValidado(reporte.encabezados());
        String poaActual = Objects.toString(idPoa, "");
        if (indiceCodigo < 0 || reporte.filas().stream()
                .allMatch(fila -> poaActual.equals(valorEn(fila.valores(), indiceCodigo + 3)))) {
            return reporte;
        }

        // Recupera los candidatos del reporte original para cruzarlos con la POA vigente.
        var encabezados = reporte.encabezados().subList(0, indiceCodigo + 1);
        var filas = new ArrayList<ExcelReader.Fila>();
        for (var fila : reporte.filas()) {
            var valores = new ArrayList<>(fila.valores().subList(0, indiceCodigo));
            valores.add(valorEn(fila.valores(), indiceCodigo + 1));
            filas.add(new ExcelReader.Fila(fila.numero(), valores));
        }
        return validarEsigef(new ExcelReader.Tabla(reporte.hoja(), encabezados, filas), idPoa);
    }

    public record FilaConsultaEsigef(String descripcion, String renglon, String codigo) {}

    @Transactional(readOnly = true)
    public List<FilaConsultaEsigef> consultarEsigef(
            long idReporte, String descripcion, String renglon, String codigo
    ) throws Exception {
        if (descripcion.length() > 150 || renglon.length() > 150 || codigo.length() > 150) {
            throw new IllegalArgumentException("Cada filtro admite hasta 150 caracteres.");
        }
        String filtroDescripcion = normalizarBusqueda(descripcion);
        String filtroRenglon = normalizarBusqueda(renglon);
        String filtroCodigo = normalizarBusqueda(codigo);
        var reporte = leerReporteCruzado(idReporte, idPoaVigente());
        var encabezados = reporte.encabezados().stream()
                .map(EsigefExcelReader::normalizarEncabezado).toList();
        int indiceDescripcion = encabezados.indexOf("DESCRIPCION");
        int indiceRenglon = encabezados.indexOf("RENGLON");
        if (indiceRenglon < 0) {
            indiceRenglon = encabezados.indexOf("ITEM PRESUPUESTARIO");
        }
        if (indiceDescripcion < 0 || indiceRenglon < 0) {
            throw new IllegalArgumentException("El reporte no contiene descripción y renglón.");
        }

        int indiceCodigo = CruceEsigefPoa.indiceCodigoValidado(reporte.encabezados());

        List<FilaConsultaEsigef> filas = new ArrayList<>();
        for (var fila : reporte.filas()) {
            var valores = fila.valores();
            String texto = valorEn(valores, indiceDescripcion);
            String item = valorEn(valores, indiceRenglon);
            String codigoValidado = indiceCodigo >= 0 && "VALIDADO".equals(valorEn(valores, indiceCodigo + 2))
                    ? valorEn(valores, indiceCodigo) : "";
            if (normalizarBusqueda(texto).contains(filtroDescripcion)
                    && normalizarBusqueda(item).contains(filtroRenglon)
                    && normalizarBusqueda(codigoValidado).contains(filtroCodigo)) {
                filas.add(new FilaConsultaEsigef(texto, item, codigoValidado));
            }
        }
        return filas;
    }

    public record FilaPoaCombinada(List<String> valores, String codigoEsigef, BigDecimal devengado) {}

    public record PoaCombinada(String archivo, List<String> encabezados, List<FilaPoaCombinada> filas,
            String errorConsulta) {}

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public PoaCombinada consultarPoaCombinada(Long idReporte, String importe, LocalDate corte)
            throws Exception {
        Long idPoa = idPoaVigente();
        if (idPoa == null) {
            return null;
        }

        var poa = detalle(idPoa);
        var encabezados = (List<String>) poa.get("encabezados");
        var nombres = encabezados.stream().map(EsigefExcelReader::normalizarEncabezado).toList();
        int columnaCodigo = nombres.indexOf("CODIGO UNICO INTERNO");
        int columnaItem = nombres.indexOf("ITEM PRESUPUESTARIO");

        Set<CalculoDevengado.Clave> codigosValidados = Set.of();
        Map<CalculoDevengado.Clave, BigDecimal> totales = Map.of();
        String errorConsulta = null;
        if (idReporte != null) {
            try {
                var reporte = leerReporteCruzado(idReporte, idPoa);
                codigosValidados = clavesValidadas(reporte);
                if (!importe.isBlank() || corte != null) {
                    if (importe.isBlank() || importe.length() > 100 || corte == null) {
                        throw new IllegalArgumentException("Indica la columna de importe y la fecha de corte.");
                    }
                    totales = CalculoDevengado.sumar(reporte, idPoa, importe, corte);
                }
            } catch (IllegalArgumentException e) {
                errorConsulta = e.getMessage();
            }
        }

        var filas = new ArrayList<FilaPoaCombinada>();
        for (var valores : (List<List<String>>) poa.get("datos")) {
            var clave = new CalculoDevengado.Clave(valorEn(valores, columnaItem).strip(),
                    valorEn(valores, columnaCodigo).strip().toUpperCase(Locale.ROOT));
            String codigoEsigef = codigosValidados.contains(clave) ? clave.codigoUnico() : "";
            filas.add(new FilaPoaCombinada(valores, codigoEsigef, totales.get(clave)));
        }
        return new PoaCombinada((String) poa.get("archivo"), encabezados, filas, errorConsulta);
    }

    private static Set<CalculoDevengado.Clave> clavesValidadas(ExcelReader.Tabla reporte) {
        int codigo = CruceEsigefPoa.indiceCodigoValidado(reporte.encabezados());
        if (codigo < 0) {
            throw new IllegalArgumentException("Vuelve a cargar este reporte eSIGEF para cruzarlo con la POA.");
        }
        var nombres = reporte.encabezados().stream().map(EsigefExcelReader::normalizarEncabezado).toList();
        int renglon = nombres.indexOf("RENGLON");
        if (renglon < 0) {
            renglon = nombres.indexOf("ITEM PRESUPUESTARIO");
        }
        Set<CalculoDevengado.Clave> claves = new HashSet<>();
        for (var fila : reporte.filas()) {
            var valores = fila.valores();
            if ("VALIDADO".equals(valorEn(valores, codigo + 2))) {
                claves.add(new CalculoDevengado.Clave(valorEn(valores, renglon).strip(),
                        valorEn(valores, codigo).strip()));
            }
        }
        return claves;
    }

    public record ReporteDevengado(long id, String archivo) {}

    public List<ReporteDevengado> reportesParaDevengado() {
        return jdbc.query(
                "SELECT id, archivo FROM excel_importacion WHERE tipo = 'ESIGEF' ORDER BY id DESC",
                (rs, n) -> new ReporteDevengado(rs.getLong("id"), rs.getString("archivo")));
    }

    public record FilaDevengado(String actividad, String item, String codigo,
            String original, java.math.BigDecimal calculado) {}

    public record PoaParaDevengado(String archivo, List<String> columnasOriginales,
            List<FilaDevengado> filas) {}

    @Transactional(readOnly = true)
    public PoaParaDevengado consultarPoaParaDevengado(
            Long idEsigef, String columnaImporte, java.time.LocalDate corte, Integer columnaOriginal
    ) throws Exception {
        Long idPoa = idPoaVigente();
        if (idPoa == null) {
            return null;
        }
        String archivo = jdbc.queryForObject(
                "SELECT archivo FROM excel_importacion WHERE id = ?", String.class, idPoa);
        var tipoLista = new TypeReference<List<String>>() {};
        List<String> encabezados = json.readValue(jdbc.queryForObject(
                "SELECT encabezados FROM excel_importacion WHERE id = ?", String.class, idPoa), tipoLista);
        List<String> nombres = encabezados.stream().map(EsigefExcelReader::normalizarEncabezado).toList();
        int actividad = nombres.indexOf("ACTIVIDAD POA");
        int item = nombres.indexOf("ITEM PRESUPUESTARIO");
        int codigo = nombres.indexOf("CODIGO UNICO INTERNO");
        if (actividad < 0 || item < 0 || codigo < 0) {
            throw new IllegalArgumentException("La POA vigente debe incluir Actividad POA, Ítem presupuestario y Código único interno.");
        }
        List<Integer> originales = new ArrayList<>();
        for (int i = 0; i < nombres.size(); i++) {
            if (nombres.get(i).startsWith("DEVENGADO")) {
                originales.add(i);
            }
        }
        List<String> opciones = originales.stream().map(encabezados::get).toList();
        if (columnaOriginal != null && (columnaOriginal < 0 || columnaOriginal >= originales.size())) {
            throw new IllegalArgumentException("Selecciona una columna de devengado original de la POA.");
        }
        if (idEsigef == null) {
            return new PoaParaDevengado(archivo, opciones, List.of());
        }
        var totales = calcularDevengado(idEsigef, columnaImporte, corte);
        if (!Objects.equals(idPoa, idPoaVigente())) {
            throw new IllegalArgumentException("La POA vigente cambió. Vuelve a consultar.");
        }
        List<FilaDevengado> filas = new ArrayList<>();
        for (String valoresJson : jdbc.queryForList(
                "SELECT valores FROM excel_fila WHERE importacion_id = ? ORDER BY numero_fila, id",
                String.class, idPoa)) {
            List<String> valores = json.readValue(valoresJson, tipoLista);
            String itemPoa = celdaDevengado(valores, item).strip();
            String codigoPoa = celdaDevengado(valores, codigo).strip();
            String original = columnaOriginal == null ? ""
                    : celdaDevengado(valores, originales.get(columnaOriginal));
            var clave = new CalculoDevengado.Clave(itemPoa, codigoPoa.toUpperCase(Locale.ROOT));
            filas.add(new FilaDevengado(celdaDevengado(valores, actividad), itemPoa,
                    codigoPoa, original, totales.get(clave)));
        }
        return new PoaParaDevengado(archivo, opciones, filas);
    }

    private static String celdaDevengado(List<String> valores, int indice) {
        return indice < valores.size() && valores.get(indice) != null ? valores.get(indice) : "";
    }

    public Long idPoaVigente() {

        List<Long> ids = jdbc.queryForList(
            """
            SELECT id
            FROM excel_importacion
            WHERE tipo = 'POA'
            ORDER BY id DESC
            LIMIT 1
            """,
            Long.class
        );

        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional
    public int resetearMatricesPoa(long idEsperado) {

        Long vigente = idPoaVigente();

        if (!Objects.equals(vigente, idEsperado)) {

            throw new IllegalArgumentException(
                "La matriz vigente cambió. "
                + "Recarga la página antes de resetear."
            );
        }

        return jdbc.update(
            """
            UPDATE excel_importacion
            SET tipo = 'POA_RETIRADO'
            WHERE tipo = 'POA' AND id <= ?
            """,
            idEsperado
        );
    }

    public List<Map<String, Object>> historial() {

        return jdbc.queryForList(
            """
            SELECT id, archivo, hoja, filas, creado
            FROM excel_importacion
            ORDER BY id DESC
            LIMIT 100
            """
        );
    }

    public Map<String, Object> consultarMatriz(
            long id,
            int pagina,
            Map<Integer, String> filtros
    ) throws Exception {

        return detalle(id, pagina, -1, "", filtros);
    }

    public Map<String, Object> detalle(
            long id
    ) throws Exception {

        return detalle(id, 1);
    }

    public Map<String, Object> detalle(
            long id,
            int pagina
    ) throws Exception {

        return detalle(id, pagina, -1, "");
    }

    public Map<String, Object> detalle(
            long id,
            int pagina,
            int columna,
            String filtro
    ) throws Exception {

        return detalle(
            id,
            pagina,
            columna,
            filtro,
            Map.of()
        );
    }

    public Map<String, Object> detalle(
            long id,
            int paginaSolicitada,
            int columna,
            String filtro,
            Map<Integer, String> filtrosColumna
    ) throws Exception {

        var registros = jdbc.queryForList(
            """
            SELECT
                id,
                archivo,
                hoja,
                encabezados,
                filas,
                creado,
                subido_por
            FROM excel_importacion
            WHERE id = ?
            """,
            id
        );

        if (registros.isEmpty()) {
            throw new IllegalArgumentException(
                "No existe esa importación."
            );
        }

        Map<String, Object> matriz = new LinkedHashMap<>();

        registros.get(0).forEach((nombre, valor) ->
            matriz.put(
                nombre.toLowerCase(Locale.ROOT),
                valor
            )
        );

        List<String> encabezados = json.readValue(
            matriz.get("encabezados").toString(),
            new TypeReference<List<String>>() {}
        );

        if (columna < -1
                || columna >= encabezados.size()
                || filtro == null
                || filtro.length() > 150) {

            throw new IllegalArgumentException(
                "Filtro inválido."
            );
        }

        if (filtrosColumna == null) {
            throw new IllegalArgumentException(
                "Los filtros de columna no son válidos."
            );
        }

        for (var campo : filtrosColumna.entrySet()) {

            if (campo.getKey() == null
                    || campo.getKey() < 0
                    || campo.getKey() >= encabezados.size()
                    || campo.getValue() == null
                    || campo.getValue().length() > 150) {

                throw new IllegalArgumentException(
                    "Filtro de columna inválido."
                );
            }
        }

        List<List<String>> coincidencias = new ArrayList<>();

        String busqueda = normalizarBusqueda(filtro);

        // Lee todas las filas: no aplica LIMIT ni OFFSET.
        var filasGuardadas = jdbc.queryForList(
            """
            SELECT valores
            FROM excel_fila
            WHERE importacion_id = ?
            ORDER BY numero_fila, id
            """,
            id
        );

        for (var registro : filasGuardadas) {

            List<String> valores = json.readValue(
                registro.get("valores").toString(),
                new TypeReference<List<String>>() {}
            );

            boolean coincide;

            if (busqueda.isEmpty()) {

                coincide = true;

            } else if (columna == -1) {

                coincide = valores.stream().anyMatch(
                    valor -> normalizarBusqueda(valor)
                        .contains(busqueda)
                );

            } else {

                coincide = normalizarBusqueda(
                    valorEn(valores, columna)
                ).contains(busqueda);
            }

            // Todas las condiciones de columna deben cumplirse.
            if (coincide) {

                for (var campo : filtrosColumna.entrySet()) {

                    String valorCelda = normalizarBusqueda(
                        valorEn(valores, campo.getKey())
                    );

                    String valorFiltro = normalizarBusqueda(
                        campo.getValue()
                    );

                    if (!valorCelda.contains(valorFiltro)) {
                        coincide = false;
                        break;
                    }
                }
            }

            if (coincide) {
                coincidencias.add(valores);
            }
        }

        // Se muestran todas las coincidencias en una sola vista.
        matriz.put("encabezados", encabezados);
        matriz.put("filasFiltradas", coincidencias.size());
        matriz.put("datos", coincidencias);

        matriz.put(
            "primerResultado",
            coincidencias.isEmpty() ? 0 : 1
        );

        matriz.put(
            "ultimoResultado",
            coincidencias.size()
        );

        // Compatibilidad con llamadas anteriores del controlador.
        // Estos atributos no dividen los datos en páginas.
        matriz.put("pagina", 1);
        matriz.put("paginas", 1);

        matriz.put(
            "tiposColumnas",
            encabezados.stream()
                .map(this::tipoColumna)
                .toList()
        );

        return matriz;
    }

    private static String valorEn(
            List<String> valores,
            int indice
    ) {

        if (indice < 0 || indice >= valores.size()) {
            return "";
        }

        String valor = valores.get(indice);

        return valor == null ? "" : valor;
    }

    private static String normalizarBusqueda(String texto) {

        if (texto == null) {
            return "";
        }

        return Normalizer.normalize(
            texto,
            Normalizer.Form.NFD
        )
        .replaceAll("\\p{M}", "")
        .strip()
        .toLowerCase(Locale.ROOT);
    }

    private String tipoColumna(String encabezado) {

        String nombre = normalizarBusqueda(
            encabezado
        ).replace('_', ' ');

        if (nombre.contains("descripcion")
                || nombre.contains("comentario")
                || nombre.contains("resolucion")
                || nombre.contains("medio de verificacion")
                || nombre.contains("actividad poa")
                || nombre.startsWith("nivel")
                || nombre.startsWith("nombre")) {

            return "columna-texto";
        }

        if (nombre.contains("monto")
                || nombre.contains("saldo")
                || nombre.contains("presupuesto")
                || nombre.contains("certificado")
                || nombre.contains("comprometido")
                || nombre.contains("devengado")
                || nombre.contains("codificado")
                || nombre.contains("reformas")
                || nombre.equals("requerido")) {

            return "columna-monto";
        }

        return "columna-dato";
    }
    @SuppressWarnings("unchecked")
public Map<String, Object> consultarConCombos(
        long id,
        Map<Integer, String> textos,
        Map<Integer, String> selecciones
) throws Exception {

    // Obtiene la matriz completa, sin filtros ni paginación.
    Map<String, Object> matriz = detalle(id);

    List<String> encabezados =
        (List<String>) matriz.get("encabezados");

    List<List<String>> filas =
        (List<List<String>>) matriz.get("datos");

    for (var campo : textos.entrySet()) {
        if (campo.getKey() < 0
                || campo.getKey() >= encabezados.size()
                || campo.getValue() == null
                || campo.getValue().length() > 150) {
            throw new IllegalArgumentException(
                "Filtro de texto inválido."
            );
        }
    }

    for (var campo : selecciones.entrySet()) {
        if (campo.getKey() < 0
                || campo.getKey() >= encabezados.size()
                || campo.getValue() == null
                || campo.getValue().length() > 10000) {
            throw new IllegalArgumentException(
                "Selección de columna inválida."
            );
        }
    }

    // Las opciones se generan con todos los datos de la matriz.
    Map<Integer, List<String>> opciones = new LinkedHashMap<>();

    for (int columna = 0;
            columna < encabezados.size();
            columna++) {

        Set<String> valoresUnicos = new TreeSet<>();

        for (List<String> fila : filas) {
            String valor = valorEn(fila, columna).strip();

            if (!valor.isEmpty()) {
                valoresUnicos.add(valor);
            }
        }

        opciones.put(
            columna,
            new ArrayList<>(valoresUnicos)
        );
    }

    List<List<String>> resultados = new ArrayList<>();

    for (List<String> fila : filas) {

        boolean coincide = true;

        // La búsqueda escrita encuentra coincidencias parciales.
        for (var campo : textos.entrySet()) {

            String valor = normalizarBusqueda(
                valorEn(fila, campo.getKey())
            );

            String textoBuscado = normalizarBusqueda(
                campo.getValue()
            );

            if (!valor.contains(textoBuscado)) {
                coincide = false;
                break;
            }
        }

        // La opción elegida debe coincidir exactamente.
        if (coincide) {

            for (var campo : selecciones.entrySet()) {

                String valor = valorEn(
                    fila,
                    campo.getKey()
                ).strip();

                if (!valor.equals(campo.getValue().strip())) {
                    coincide = false;
                    break;
                }
            }
        }

        if (coincide) {
            resultados.add(fila);
        }
    }

    matriz.put("datos", resultados);
    matriz.put("filasFiltradas", resultados.size());
    matriz.put("opcionesColumnas", opciones);

    matriz.put(
        "primerResultado",
        resultados.isEmpty() ? 0 : 1
    );

    matriz.put("ultimoResultado", resultados.size());
    matriz.put("pagina", 1);
    matriz.put("paginas", 1);

    return matriz;
}
}