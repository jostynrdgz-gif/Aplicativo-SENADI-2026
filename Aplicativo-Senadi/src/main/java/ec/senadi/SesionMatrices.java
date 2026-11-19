package ec.senadi;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {
    ImportacionController.class, EsigefConsultaController.class, DevengadoController.class
})
public class SesionMatrices {

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
}
