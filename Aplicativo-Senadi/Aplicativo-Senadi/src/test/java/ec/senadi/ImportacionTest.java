package ec.senadi;

import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:senadi;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver", "spring.config.import=", "senadi.login.planificacion.password=PlanTest123!", "senadi.login.financiero.password=FinTest123!"})
class ImportacionTest {
    @Autowired ImportacionService service;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void limpiar() { jdbc.execute("ALTER TABLE excel_fila DROP CONSTRAINT IF EXISTS prueba_fallo"); jdbc.update("DELETE FROM excel_fila"); jdbc.update("DELETE FROM excel_importacion"); }
    byte[] excel(boolean legacy, String segundoEncabezado, boolean formula) throws Exception {
        try (Workbook libro=legacy?new HSSFWorkbook():new XSSFWorkbook(); var output=new ByteArrayOutputStream()) {
            var hoja=libro.createSheet("Personas"); var encabezado=hoja.createRow(0);
            encabezado.createCell(0).setCellValue("Cedula"); encabezado.createCell(1).setCellValue(segundoEncabezado);
            var fila=hoja.createRow(1); fila.createCell(0).setCellValue("0012345678"); fila.createCell(1).setCellValue("Jostyn <script>");
            if(formula) fila.getCell(1).setCellFormula("1+1");
            hoja.createRow(2); var otra=hoja.createRow(3); otra.createCell(0).setCellValue("0098765432"); otra.createCell(1).setCellValue("Prueba");
            libro.write(output); return output.toByteArray();
        }
    }
    MockMultipartFile archivo(byte[] bytes, String extension) { return new MockMultipartFile("archivo","prueba."+extension,"application/octet-stream",bytes); }
    @Test void importaXlsxConCerosYOmiteFilaVacia() throws Exception {
        long id=service.importar(archivo(excel(false,"Nombre",false),"xlsx"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM excel_fila WHERE importacion_id=?",Integer.class,id));
        assertTrue(service.detalle(id).get("datos").toString().contains("0012345678"));
        assertTrue(service.detalle(id).get("datos").toString().contains("Jostyn <script>"));
    }
    @Test void importaFormatoXls() throws Exception { service.importar(archivo(excel(true,"Nombre",false),"xls")); assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM excel_fila",Integer.class)); }
    @Test void rechazaEncabezadosDuplicadosSinGuardar() throws Exception {
        assertThrows(IllegalArgumentException.class,()->service.importar(archivo(excel(false,"cedula",false),"xlsx")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM excel_importacion",Integer.class));
    }
    @Test void rechazaFormula() { assertThrows(IllegalArgumentException.class,()->service.importar(archivo(excel(false,"Nombre",true),"xlsx"))); }
    @Test void rollbackSiFallaInsercionDeFilas() throws Exception {
        jdbc.execute("ALTER TABLE excel_fila ADD CONSTRAINT prueba_fallo CHECK(numero_fila < 3)");
        assertThrows(Exception.class,()->service.importar(archivo(excel(false,"Nombre",false),"xlsx")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM excel_importacion",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM excel_fila",Integer.class));
    }
    @Test void rechazaArchivoDisfrazado() { assertThrows(Exception.class,()->service.importar(archivo("no soy excel".getBytes(),"xlsx"))); }
}
