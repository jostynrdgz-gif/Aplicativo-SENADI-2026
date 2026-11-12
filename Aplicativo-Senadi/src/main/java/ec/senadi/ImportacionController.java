package ec.senadi;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ImportacionController {
    private final ImportacionService service;
    public ImportacionController(ImportacionService service) { this.service = service; }

    @ModelAttribute
    public void usuarioActual(Authentication authentication, Model model) {
        if (authentication == null) return;
        boolean planificacion = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PLANIFICACION"));
        model.addAttribute("usuarioActual", authentication.getName());
        model.addAttribute("rolActual", planificacion ? "PLANIFICACION" : "FINANCIERO");
        model.addAttribute("puedeImportar", planificacion);
    }

    @GetMapping("/")
    public String inicio(@RequestParam(defaultValue = "1") int pagina, Model model) throws Exception {
        Long id = service.idPoaVigente();
        model.addAttribute("lote", id == null ? null : service.detalle(id, pagina));
        return "inicio";
    }

    @PostMapping("/importaciones")
    public String importar(@RequestParam("archivo") MultipartFile archivo,
            Authentication authentication, RedirectAttributes flash) {
        try {
            service.importarPoa(archivo, authentication.getName());
            flash.addFlashAttribute("mensaje", "La matriz POA se guardó correctamente y ya está disponible para Financiero.");
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "No se pudo importar. Revisa el archivo y la conexión a MySQL. No se guardaron cambios.");
        }
        return "redirect:/";
    }

    @GetMapping("/importaciones/{id}")
    public String detalle(@PathVariable long id) {
        Long vigente = service.idPoaVigente();
        if (vigente == null || vigente.longValue() != id) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "redirect:/";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String exceso(RedirectAttributes flash) {
        flash.addFlashAttribute("error", "El archivo excede el límite de 10 MB.");
        return "redirect:/";
    }
}
