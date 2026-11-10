package ec.senadi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Controller
public class ImportacionController {
    private final ImportacionService service;
    public ImportacionController(ImportacionService service) { this.service=service; }
    @GetMapping("/") public String inicio(Model model) { model.addAttribute("lotes",service.historial()); return "inicio"; }
    @PostMapping("/importaciones") public String importar(@RequestParam("archivo") MultipartFile archivo, RedirectAttributes flash) {
        try { long id=service.importar(archivo); flash.addFlashAttribute("mensaje","Importación guardada correctamente en la base de datos."); return "redirect:/importaciones/"+id; }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (Exception e) { flash.addFlashAttribute("error","No se pudo importar. Revisa el archivo y la conexión a MySQL. No se guardaron cambios."); }
        return "redirect:/";
    }
    @GetMapping("/importaciones/{id}") public String detalle(@PathVariable long id, Model model, RedirectAttributes flash) throws Exception {
        try { model.addAttribute("lote", service.detalle(id)); return "detalle"; }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error",e.getMessage()); return "redirect:/"; }
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String exceso(RedirectAttributes flash) { flash.addFlashAttribute("error","El archivo excede el límite de 10 MB."); return "redirect:/"; }
}
