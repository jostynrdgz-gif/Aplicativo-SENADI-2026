package ec.senadi;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ImportacionController {

    private final ImportacionService service;

    public ImportacionController(ImportacionService service) {
        this.service = service;
    }

    @ModelAttribute
    public void usuarioActual(
            Authentication authentication,
            Model model
    ) {

        if (authentication == null) {
            return;
        }

        boolean planificacion = authentication.getAuthorities()
            .stream()
            .anyMatch(autoridad ->
                autoridad.getAuthority().equals("ROLE_PLANIFICACION")
            );

        model.addAttribute(
            "usuarioActual",
            authentication.getName()
        );

        model.addAttribute(
            "rolActual",
            planificacion ? "PLANIFICACION" : "FINANCIERO"
        );

        model.addAttribute("puedeImportar", planificacion);
    }

    @GetMapping("/")
    public String inicio(
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "-1") int columna,
            @RequestParam(defaultValue = "") String filtro,
            @RequestParam Map<String, String> parametros,
            Model model
    ) throws Exception {

        Long id = service.idPoaVigente();

        Map<Integer, String> filtrosColumna =
            new LinkedHashMap<>();

        try {
            for (var campo : parametros.entrySet()) {

                // Acepta los nombres de filtros de ambas vistas.
                if (campo.getKey().matches("[cf][0-9]+")) {

                    int indice = Integer.parseInt(
                        campo.getKey().substring(1)
                    );

                    String valor = campo.getValue();

                    if (indice > 99 || valor.length() > 150) {
                        throw new IllegalArgumentException(
                            "Filtro inválido."
                        );
                    }

                    if (!valor.isBlank()) {
                        filtrosColumna.put(indice, valor.strip());
                    }
                }
            }

            model.addAttribute(
                "lote",
                id == null
                    ? null
                    : service.detalle(
                        id,
                        pagina,
                        columna,
                        filtro,
                        filtrosColumna
                    )
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Revisa los filtros ingresados."
            );
        }

        model.addAttribute("columnaFiltro", columna);
        model.addAttribute("filtro", filtro);

        // Compatibilidad con el inicio.html escrito manualmente.
        model.addAttribute("filtrosMatriz", filtrosColumna);

        // Compatibilidad con la otra versión de la vista.
        model.addAttribute("filtrosColumna", filtrosColumna);

        model.addAttribute(
            "cantidadFiltros",
            filtrosColumna.size() + (filtro.isBlank() ? 0 : 1)
        );

        return "inicio";
    }

    @PostMapping("/importaciones")
    public String importar(
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication,
            RedirectAttributes flash
    ) {

        try {
            service.importarMatrizPoa(
                archivo,
                authentication.getName()
            );

            flash.addFlashAttribute(
                "mensaje",
                "La matriz POA se guardó correctamente "
                + "y ya está disponible para Financiero."
            );

        } catch (IllegalArgumentException e) {

            flash.addFlashAttribute("error", e.getMessage());

        } catch (Exception e) {

            flash.addFlashAttribute(
                "error",
                "No se pudo importar. Revisa el archivo "
                + "y la conexión a MySQL. "
                + "No se guardaron cambios."
            );
        }

        return "redirect:/";
    }

    @GetMapping("/importaciones/{id}")
    public String detalle(@PathVariable long id) {

        Long vigente = service.idPoaVigente();

        if (vigente == null || vigente.longValue() != id) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND
            );
        }

        return "redirect:/";
    }

    @PostMapping("/matrices/resetear")
    public String resetearMatrices(
            @RequestParam long matrizId,
            RedirectAttributes flash
    ) {

        try {
            int retiradas = service.resetearMatricesPoa(matrizId);

            flash.addFlashAttribute(
                "mensaje",
                "Se retiraron " + retiradas + " matrices POA. "
                + "Ahora puedes publicar una nueva. "
                + "Los datos se conservaron como respaldo."
            );

        } catch (IllegalArgumentException e) {

            flash.addFlashAttribute("error", e.getMessage());

        } catch (Exception e) {

            flash.addFlashAttribute(
                "error",
                "No se pudo resetear. Revisa la conexión "
                + "y el permiso UPDATE sobre tipo en MySQL."
            );
        }

        return "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String exceso(RedirectAttributes flash) {

        flash.addFlashAttribute(
            "error",
            "El archivo excede el límite de 10 MB."
        );

        return "redirect:/";
    }
}