package ec.senadi;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:roles;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.config.import=",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema.sql",
    "senadi.login.planificacion.password=PlanTest123!",
    "senadi.login.financiero.password=FinTest123!"
})
@AutoConfigureMockMvc
class RolesPoaTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ImportacionService service;

    @BeforeEach void limpiar() {
        jdbc.update("DELETE FROM excel_fila");
        jdbc.update("DELETE FROM excel_importacion");
    }
    MockMultipartFile excel() throws Exception {
        try (var w = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var s = w.createSheet("POA");
            s.createRow(0).createCell(0).setCellValue("Actividad");
            s.createRow(1).createCell(0).setCellValue("Compra <script>alert(1)</script>");
            w.write(out);
            return new MockMultipartFile("archivo", "poa.xlsx", "application/octet-stream", out.toByteArray());
        }
    }
    @Test void anonimoDebeIniciarSesion() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }
    @Test void loginAsignaCadaRolYRechazaClaveIncorrecta() throws Exception {
        mvc.perform(formLogin().user("planificacion").password("PlanTest123!"))
            .andExpect(authenticated().withRoles("PLANIFICACION"));
        mvc.perform(formLogin().user("financiero").password("FinTest123!"))
            .andExpect(authenticated().withRoles("FINANCIERO"));
        mvc.perform(formLogin().user("financiero").password("incorrecta"))
            .andExpect(unauthenticated());
    }
    @Test void financieroVeMensajeSinPoaYNoTieneFormulario() throws Exception {
        // Una importación genérica de prueba no constituye una publicación POA.
        service.importar(excel());
        mvc.perform(get("/").with(user("financiero").roles("FINANCIERO")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("No se ha subido ninguna matriz POA.")))
            .andExpect(content().string(containsString("FINANCIERO")))
            .andExpect(content().string(not(containsString("type=\"file\""))))
            .andExpect(content().string(not(containsString("Últimas importaciones"))));
    }
    @Test void financieroNoPuedeImportarAunqueEnviePeticionDirecta() throws Exception {
        mvc.perform(multipart("/importaciones").file(excel()).with(csrf())
                .with(user("financiero").roles("FINANCIERO")))
            .andExpect(status().isForbidden());
        assertNull(service.idPoaVigente());
    }
    @Test void planificacionPublicaYFinancieroConsulta() throws Exception {
        mvc.perform(multipart("/importaciones").file(excel()).with(csrf())
                .with(user("planificacion").roles("PLANIFICACION")))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        assertNotNull(service.idPoaVigente());
        assertEquals("planificacion", jdbc.queryForObject("SELECT subido_por FROM excel_importacion", String.class));
        mvc.perform(get("/").with(user("financiero").roles("FINANCIERO")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("poa.xlsx")))
            .andExpect(content().string(containsString("&lt;script&gt;")))
            .andExpect(content().string(not(containsString("type=\"file\""))));
        mvc.perform(get("/").with(user("planificacion").roles("PLANIFICACION")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("type=\"file\"")))
            .andExpect(content().string(containsString("PLANIFICACION")));
    }
    @Test void importacionFallidaConservaPoaVigente() throws Exception {
        long id = service.importarPoa(excel(), "planificacion");
        mvc.perform(multipart("/importaciones").file(new MockMultipartFile("archivo", "malo.xlsx", "application/octet-stream", "no es excel".getBytes()))
                .with(csrf()).with(user("planificacion").roles("PLANIFICACION")))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertEquals(id, service.idPoaVigente());
    }
    @Test void soloSeExponeMatrizVigenteYPaginaHastaUltimaFila() throws Exception {
        long anterior = service.importarPoa(excel(), "planificacion");
        long actual = service.importarPoa(excel(), "planificacion");
        for (int i = 2; i <= 258; i++) {
            jdbc.update("INSERT INTO excel_fila(importacion_id,numero_fila,valores) VALUES(?,?,?)", actual, i + 1, "[\"Fila " + i + "\"]");
        }
        jdbc.update("UPDATE excel_importacion SET filas=258 WHERE id=?", actual);
        mvc.perform(get("/importaciones/" + anterior).with(user("financiero").roles("FINANCIERO")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/importaciones/" + actual).with(user("financiero").roles("FINANCIERO")))
            .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").param("pagina", "2").with(user("financiero").roles("FINANCIERO")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Fila 258")));
    }
    @Test void csrfEsObligatorioYCerrarSesionInvalidaAcceso() throws Exception {
        mvc.perform(multipart("/importaciones").file(excel()).with(user("planificacion").roles("PLANIFICACION")))
            .andExpect(status().isForbidden());
        var login = mvc.perform(formLogin().user("financiero").password("FinTest123!"))
                .andExpect(authenticated()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
        assertTrue(session.isInvalid());
    }
}
