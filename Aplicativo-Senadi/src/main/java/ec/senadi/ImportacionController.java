package ec.senadi;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public void usuarioActual(Authentication authentication, Model model) {
        if (authentication == null) {
            return;
        }

        boolean planificacion = authentication.getAuthorities().stream()
                .anyMatch(autoridad ->
                        "ROLE_PLANIFICACION".equals(autoridad.getAuthority()));

        model.addAttribute("usuarioActual", authentication.getName());
        model.addAttribute("rolActual", planificacion ? "PLANIFICACION" : "FINANCIERO");
        model.addAttribute("puedeImportar", planificacion);
    }

    @GetMapping("/")
    public String inicio(@RequestParam Map<String, String> parametros, Model model)
            throws Exception {
        Map<Integer, String> textos = new LinkedHashMap<>();
        Map<Integer, String> selecciones = new LinkedHashMap<>();

        try {
            for (var parametro : parametros.entrySet()) {
                String nombre = parametro.getKey();
                String valor = parametro.getValue();

                if (!nombre.matches("[fco][0-9]+") || valor.isBlank()) {
                    continue;
                }

                int indice = Integer.parseInt(nombre.substring(1));
                boolean esSeleccion = nombre.startsWith("o");
                int maximo = esSeleccion ? 10000 : 150;

                if (indice > 99 || valor.length() > maximo) {
                    throw new IllegalArgumentException("Filtro inválido.");
                }

                if (esSeleccion) {
                    selecciones.put(indice, valor.strip());
                } else {
                    textos.put(indice, valor.strip());
                }
            }

            Long idPoa = service.idPoaVigente();
            model.addAttribute("lote", idPoa == null
                    ? null
                    : service.consultarConCombos(idPoa, textos, selecciones));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Revisa los filtros ingresados.", e);
        }

        model.addAttribute("filtrosMatriz", textos);
        model.addAttribute("filtrosSeleccion", selecciones);
        model.addAttribute("cantidadFiltros", textos.size() + selecciones.size());

        return "inicio";
    }

    @PostMapping("/importaciones")
    public String importar(
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication,
            RedirectAttributes flash
    ) {
        try {
            service.importarMatrizPoa(archivo, authentication.getName());
            flash.addFlashAttribute("mensaje",
                    "La matriz POA se guardó correctamente y ya está disponible para Financiero.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo importar la POA. Revisa el archivo y la conexión a MySQL.");
        }

        return "redirect:/";
    }

    @PostMapping("/importaciones/esigef")
    public String importarEsigef(
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication,
            RedirectAttributes flash
    ) {
        try {
            service.importarEsigef(archivo, authentication.getName());
            flash.addFlashAttribute("mensaje", "El reporte eSIGEF se guardó correctamente.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo importar el reporte eSIGEF. Revisa el archivo y vuelve a intentarlo.");
        }

        return "redirect:/";
    }

    @GetMapping("/importaciones/{id}")
    public String detalle(@PathVariable("id") long id) {
        Long idPoa = service.idPoaVigente();

        if (idPoa == null || idPoa.longValue() != id) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return "redirect:/";
    }

    @PostMapping("/matrices/resetear")
    public String resetear(
            @RequestParam("matrizId") long matrizId,
            RedirectAttributes flash
    ) {
        try {
            service.resetearMatricesPoa(matrizId);
            flash.addFlashAttribute("mensaje",
                    "Las matrices POA se retiraron de la vista. Los datos se conservan como respaldo.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error",
                    "No se pudo resetear la POA. Revisa la conexión y los permisos de MySQL.");
        }

        return "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String exceso(RedirectAttributes flash) {
        flash.addFlashAttribute("error", "El archivo excede el límite de 10 MB.");
        return "redirect:/";
    }
}