package ec.senadi;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class EsigefConsultaController {
    private final ImportacionService service;

    public EsigefConsultaController(ImportacionService service) {
        this.service = service;
    }

    @GetMapping("/esigef")
    public String consultar(
            @RequestParam(name = "reporte", required = false) Long reporte,
            @RequestParam(name = "descripcion", defaultValue = "") String descripcion,
            @RequestParam(name = "renglon", defaultValue = "") String renglon,
            @RequestParam(name = "codigo", defaultValue = "") String codigo,
            Model model
    ) throws Exception {
        var reportes = service.reportesParaDevengado();
        Long seleccionado = reporte;
        if (seleccionado == null && !reportes.isEmpty()) {
            seleccionado = reportes.getFirst().id();
        }
        model.addAttribute("reporteSeleccionado", seleccionado);
        if (seleccionado != null) {
            try {
                if (Boolean.TRUE.equals(model.getAttribute("puedeImportar"))) {
                    model.addAttribute("matriz", service.consultarEsigefCompleto(seleccionado));
                } else {
                    var filas = service.consultarEsigef(seleccionado, descripcion, renglon, codigo).stream()
                            .map(fila -> List.of(fila.descripcion(), fila.renglon(),
                                    fila.codigo().isEmpty() ? "—" : fila.codigo()))
                            .toList();
                    model.addAttribute("matriz", new ImportacionService.MatrizConsultaEsigef(
                            List.of("Descripción", "Renglón / ítem presupuestario", "Código único"), filas));
                }
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorConsulta", e.getMessage());
            }
            long id = seleccionado;
            reportes.stream().filter(r -> r.id() == id).findFirst()
                    .ifPresent(r -> model.addAttribute("archivoReporte", r.archivo()));
        }
        return "esigef";
    }
}
