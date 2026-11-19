package ec.senadi;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DevengadoController {
    private final ImportacionService service;

    public DevengadoController(ImportacionService service) {
        this.service = service;
    }

    @GetMapping({"/poa-combinada", "/devengado", "/codigos-unicos"})
    public String consultar(
            @RequestParam(name = "reporte", required = false) Long reporte,
            @RequestParam(name = "importe", defaultValue = "") String importe,
            @RequestParam(name = "corte", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate corte,
            Model model
    ) throws Exception {
        var reportes = service.reportesParaDevengado();
        Long seleccionado = reporte;
        if (seleccionado == null && !reportes.isEmpty()) {
            seleccionado = reportes.getFirst().id();
        }

        model.addAttribute("reportes", reportes);
        model.addAttribute("reporteSeleccionado", seleccionado);
        model.addAttribute("importeSeleccionado", importe);
        model.addAttribute("corteSeleccionado", corte);
        model.addAttribute("matriz", service.consultarPoaCombinada(seleccionado, importe, corte));
        return "devengado";
    }
}
