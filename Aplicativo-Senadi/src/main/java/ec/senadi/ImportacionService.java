package ec.senadi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.sql.PreparedStatement;
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
    public ImportacionService(JdbcTemplate jdbc, ExcelReader reader, ObjectMapper json) {
        this.jdbc = jdbc; this.reader = reader; this.json = json;
    }
    @Transactional(rollbackFor = Exception.class)
    public long importar(MultipartFile archivo) throws Exception {
        return guardar(archivo, "GENERICO", null);
    }
    @Transactional(rollbackFor = Exception.class)
    public long importarPoa(MultipartFile archivo, String usuario) throws Exception {
        return guardar(archivo, "POA", usuario);
    }
    private long guardar(MultipartFile archivo, String tipo, String usuario) throws Exception {
        String nombre = Optional.ofNullable(archivo.getOriginalFilename()).orElse("").replace('\\','/');
        nombre = nombre.substring(nombre.lastIndexOf('/')+1);
        if (archivo.isEmpty() || archivo.getSize() > 10 * 1024 * 1024 || nombre.length() > 255 || !nombre.toLowerCase(Locale.ROOT).matches(".*\\.xlsx?"))
            throw new IllegalArgumentException("Selecciona un archivo .xls o .xlsx de hasta 10 MB.");
        ExcelReader.Tabla tabla;
        try (var input = archivo.getInputStream()) { tabla = reader.leer(input); }
        String headers = json.writeValueAsString(tabla.encabezados());
        var key = new GeneratedKeyHolder();
        final String fileName = nombre;
        jdbc.update(con -> {
            PreparedStatement stmt = con.prepareStatement("INSERT INTO excel_importacion(archivo,hoja,encabezados,filas,tipo,subido_por) VALUES (?,?,?,?,?,?)", new String[]{"id"});
            stmt.setString(1,fileName); stmt.setString(2,tabla.hoja()); stmt.setString(3,headers); stmt.setInt(4,tabla.filas().size());
            stmt.setString(5,tipo); stmt.setString(6,usuario); return stmt;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        List<Object[]> datos = new ArrayList<>();
        for (var fila : tabla.filas()) datos.add(new Object[]{id, fila.numero(), json.writeValueAsString(fila.valores())});
        jdbc.batchUpdate("INSERT INTO excel_fila(importacion_id,numero_fila,valores) VALUES (?,?,?)", datos);
        return id;
    }
    public List<Map<String,Object>> historial() {
        return jdbc.queryForList("SELECT id,archivo,hoja,filas,creado FROM excel_importacion ORDER BY id DESC LIMIT 100");
    }
    public Map<String,Object> detalle(long id) throws Exception {
        return detalle(id, 1);
    }
    public Long idPoaVigente() {
        var ids = jdbc.queryForList("SELECT id FROM excel_importacion WHERE tipo='POA' ORDER BY id DESC LIMIT 1", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }
    public Map<String,Object> detalle(long id, int paginaSolicitada) throws Exception {
        var lotes = jdbc.queryForList("SELECT id,archivo,hoja,encabezados,filas,creado,subido_por FROM excel_importacion WHERE id=?", id);
        if (lotes.isEmpty()) throw new IllegalArgumentException("No existe esa importación.");
        Map<String,Object> lote = new LinkedHashMap<>();
        lotes.get(0).forEach((columna, valor) -> lote.put(columna.toLowerCase(Locale.ROOT), valor));
        int paginas = Math.max(1, (((Number) lote.get("filas")).intValue() + 199) / 200);
        int pagina = Math.max(1, Math.min(paginaSolicitada, paginas));
        lote.put("pagina", pagina);
        lote.put("paginas", paginas);
        lote.put("encabezados", json.readValue(lote.get("encabezados").toString(), new TypeReference<List<String>>(){}));
        List<List<String>> filas = new ArrayList<>();
        for (var fila : jdbc.queryForList("SELECT valores FROM excel_fila WHERE importacion_id=? ORDER BY numero_fila LIMIT 200 OFFSET ?",id,(pagina-1)*200))
            filas.add(json.readValue(fila.get("valores").toString(), new TypeReference<List<String>>(){}));
        lote.put("datos",filas); return lote;
    }
}
