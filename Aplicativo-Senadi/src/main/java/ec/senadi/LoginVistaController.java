package ec.senadi;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginVistaController {

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }
}