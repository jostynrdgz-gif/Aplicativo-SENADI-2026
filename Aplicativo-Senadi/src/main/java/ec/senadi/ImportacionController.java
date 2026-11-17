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
        @RequestParam Map<String, String> parametros,
        Model model
    ) throws Exception {

    Map<Integer, String> textos = new LinkedHashMap<>();
    Map<Integer, String> selecciones = new LinkedHashMap<>();

    try {
        for (var parametro : parametros.entrySet()) {

            String nombre = parametro.getKey();
            String valor = parametro.getValue();

            // f0, f1...: texto.
            // o0, o1...: opción seleccionada.
            // También acepta c0, c1... de la vista anterior.
            if (nombre.matches("[fco][0-9]+")) {

                int indice = Integer.parseInt(
                    nombre.substring(1)
                );

                boolean esSeleccion = nombre.startsWith("o");
                int longitudMaxima = esSeleccion ? 10000 : 150;

                if (indice > 99
                        || valor.length() > longitudMaxima) {
                    throw new IllegalArgumentException(
                        "Filtro inválido."
                    );
                }

                if (!valor.isBlank()) {

                    if (esSeleccion) {
                        selecciones.put(indice, valor.strip());
                    } else {
                        textos.put(indice, valor.strip());
                    }
                }
            }
        }

        Long id = service.idPoaVigente();

        model.addAttribute(
            "lote",
            id == null
                ? null
                : service.consultarConCombos(
                    id,
                    textos,
                    selecciones
                )
        );

    } catch (IllegalArgumentException e) {

        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Revisa los filtros ingresados."
        );
    }

    model.addAttribute("filtrosMatriz", textos);
    model.addAttribute("filtrosSeleccion", selecciones);

    model.addAttribute(
        "cantidadFiltros",
        textos.size() + selecciones.size()
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