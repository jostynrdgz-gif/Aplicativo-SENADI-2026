package ec.senadi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.text.Normalizer;
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

            if ("POA".equals(tipo)) {
                tabla = new PoaExcelReader().leer(input);
            } else {
                tabla = reader.leer(input);
            }
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
}