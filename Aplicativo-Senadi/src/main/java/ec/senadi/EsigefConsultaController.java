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
        Long seleccionado = seleccionarReporte(reporte, model);
        model.addAttribute("descripcion", descripcion);
        model.addAttribute("renglon", renglon);
        model.addAttribute("codigo", codigo);
        model.addAttribute("filas", List.of());
        if (seleccionado != null) {
            try {
                model.addAttribute("filas", service.consultarEsigef(seleccionado, descripcion, renglon, codigo));
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorConsulta", e.getMessage());
            }
        }
        return "esigef";
    }

    private Long seleccionarReporte(Long solicitado, Model model) {
        var reportes = service.reportesParaDevengado();
        Long seleccionado = solicitado;
        if (seleccionado == null && !reportes.isEmpty()) {
            seleccionado = reportes.getFirst().id();
        }
        model.addAttribute("reportes", reportes);
        model.addAttribute("reporteSeleccionado", seleccionado);
        return seleccionado;
    }
}
