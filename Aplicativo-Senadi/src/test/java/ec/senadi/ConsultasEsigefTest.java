package ec.senadi;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:consultasesigef;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.config.import=",
    "spring.sql.init.mode=always",
    "senadi.login.planificacion.password=PlanTest123!",
    "senadi.login.financiero.password=FinTest123!"
})
@AutoConfigureMockMvc
@Transactional
class ConsultasEsigefTest {
    @Autowired MockMvc mvc;
    @Autowired ImportacionService service;

    @Test
    void consultaFinancieraFiltraElReporteYComparaDevengadoConCorte() throws Exception {
        service.importarPoa(excel("poa.xlsx", 2,
                new String[]{"ACTIVIDAD POA", "ÍTEM PRESUPUESTARIO", "CÓDIGO ÚNICO INTERNO", "DEVENGADO ORIGINAL"},
                new String[]{"Compra de insumos", "530104", "DF_02", "19,00"}), "planificacion");
        long id = service.importarEsigef(excel("reporte.xlsx", 0,
                new String[]{"DESCRIPCION", "RENGLON", "ESTADO", "FEC_APROBADO", "MONTO_RENGLON"},
                new String[]{"Adquisición DF_02", "530104", "APROBADO", "4/8/2026", "25,50"},
                new String[]{"Posterior DF_02", "530104", "APROBADO", "5/8/2026", "100"},
                new String[]{"Observada DF_02", "530104", "ERRADO", "4/8/2026", "999"},
                new String[]{"Sin código", "530104", "APROBADO", "4/8/2026", "99"}), "planificacion");

        mvc.perform(get("/esigef")).andExpect(status().is3xxRedirection());
        var login = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"")
                .matcher(login.getResponse().getContentAsString());
        assertTrue(csrf.find());
        var session = (MockHttpSession) login.getRequest().getSession(false);
        String token = csrf.group(1);
        var autenticado = mvc.perform(post("/login").session(session).param("_csrf", token)
                .param("username", "financiero").param("password", "FinTest123!"))
                .andExpect(redirectedUrl("/esigef")).andReturn();
        var financiero = (MockHttpSession) autenticado.getRequest().getSession(false);

        mvc.perform(get("/esigef").session(financiero).param("reporte", Long.toString(id))
                .param("descripcion", "ADQUISICION").param("codigo", "df_02"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DF_02")))
                .andExpect(content().string(not(containsString("MONTO_RENGLON"))))
                .andExpect(content().string(not(containsString("Posterior DF_02"))));

        mvc.perform(get("/devengado").session(financiero).param("reporte", Long.toString(id))
                .param("importe", "MONTO_RENGLON").param("corte", "2026-08-04").param("original", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("19,00")))
                .andExpect(content().string(containsString("25,50")))
                .andExpect(content().string(not(containsString("125,50"))));

        mvc.perform(get("/esigef").session(financiero).param("reporte", Long.toString(service.idPoaVigente())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No existe ese reporte eSIGEF.")));
    }

    @Test
    void combinaCodigoYDevengadoEnLaMismaFilaAunqueEsigefSeCarguePrimero() throws Exception {
        var planificacion = user("planificacion").roles("PLANIFICACION");
        var financiero = user("financiero").roles("FINANCIERO");
        var esigef = excel("reporte.xlsx", 0,
                new String[]{"DESCRIPCION", "RENGLON", "ESTADO", "FEC_APROBADO", "MONTO_RENGLON"},
                new String[]{"Compra df_02 DF_02", "530104", "APROBADO", "4/8/2026", "25,50"},
                new String[]{"Gasto DF_02 y DA_01", "530105", "APROBADO", "4/8/2026", "100"});

        mvc.perform(multipart("/importaciones").file(esigef).param("tipo", "ESIGEF")
                .with(csrf()).with(financiero)).andExpect(status().isForbidden());
        var carga = mvc.perform(multipart("/importaciones").file(esigef).param("tipo", "ESIGEF")
                .with(csrf()).with(planificacion)).andExpect(status().is3xxRedirection()).andReturn();
        long id = service.reportesParaDevengado().getFirst().id();
        assertEquals("/esigef?reporte=" + id, carga.getResponse().getRedirectedUrl());

        mvc.perform(get("/poa-combinada").with(financiero))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sube una matriz POA")));

        var poa = excel("poa.xlsx", 2,
                new String[]{"ACTIVIDAD POA", "ÍTEM PRESUPUESTARIO", "CÓDIGO ÚNICO INTERNO", "DEVENGADO ORIGINAL"},
                new String[]{"Compra de insumos", "530104", "DF_02", "19,00"},
                new String[]{"Otro gasto", "530105", "DA_01", "6,00"});
        mvc.perform(multipart("/importaciones").file(poa).param("tipo", "POA")
                .with(csrf()).with(planificacion)).andExpect(redirectedUrl("/"));
        mvc.perform(get("/").with(planificacion))
                .andExpect(status().isOk()).andExpect(content().string(containsString("poa.xlsx")));
        mvc.perform(get("/poa-combinada").param("reporte", Long.toString(id)).with(financiero))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Indica la columna de importe"))));
        var respuesta = mvc.perform(get("/poa-combinada").param("reporte", Long.toString(id))
                .param("importe", "MONTO_RENGLON").param("corte", "2026-08-04").with(financiero))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-current=\"page\">POACOMBINADA")))
                .andExpect(content().string(containsString("DEVENGADO ORIGINAL")))
                .andExpect(content().string(containsString("Código único extraído del eSIGEF")))
                .andExpect(content().string(containsString("25,50")))
                .andExpect(content().string(not(containsString("type=\"file\""))))
                .andReturn().getResponse().getContentAsString();
        var pestanas = respuesta.substring(respuesta.indexOf("<nav"), respuesta.indexOf("</nav>"));
        assertEquals(2L, Pattern.compile("<a ").matcher(pestanas).results().count());

        var combinada = service.consultarPoaCombinada(id, "MONTO_RENGLON", LocalDate.of(2026, 8, 4));
        var primera = combinada.filas().get(0);
        assertEquals("19,00", primera.valores().getLast());
        assertEquals("DF_02", primera.codigoEsigef());
        assertEquals(new BigDecimal("25.50"), primera.devengado());
        assertEquals("", combinada.filas().get(1).codigoEsigef());
    }

    private MockMultipartFile excel(String nombre, int filaEncabezados, String[]... lineas) throws Exception {
        try (var libro = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Datos");
            for (int i = 0; i < lineas.length; i++) {
                var fila = hoja.createRow(filaEncabezados + i);
                for (int j = 0; j < lineas[i].length; j++) {
                    fila.createCell(j).setCellValue(lineas[i][j]);
                }
            }
            libro.write(bytes);
            return new MockMultipartFile("archivo", nombre, "application/octet-stream", bytes.toByteArray());
        }
    }
}
