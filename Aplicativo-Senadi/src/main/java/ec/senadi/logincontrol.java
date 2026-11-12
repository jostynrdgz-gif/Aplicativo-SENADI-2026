package ec.senadi;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class logincontrol {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService usuariosDePrueba(PasswordEncoder encoder,
            @Value("${senadi.login.planificacion.password}") String clavePlanificacion,
            @Value("${senadi.login.financiero.password}") String claveFinanciero) {
        if (clavePlanificacion.isBlank() || claveFinanciero.isBlank()) {
            throw new IllegalArgumentException("Configura las contraseñas de ambos usuarios en application-local.properties.");
        }
        var planificacion = User.withUsername("planificacion")
                .password(encoder.encode(clavePlanificacion))
                .roles("PLANIFICACION")
                .build();
        var financiero = User.withUsername("financiero")
                .password(encoder.encode(claveFinanciero))
                .roles("FINANCIERO")
                .build();
        return new InMemoryUserDetailsManager(planificacion, financiero);
    }

    @Bean
    public SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(permisos -> permisos
                .requestMatchers(
                    "/login",
                    "/error",
                    "/estilos.css",
                    "/css/**",
                    "/img/**"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/importaciones").hasRole("PLANIFICACION")
                .requestMatchers(HttpMethod.GET, "/", "/importaciones/*").hasAnyRole("PLANIFICACION", "FINANCIERO")
                .anyRequest().denyAll()
            )
            .formLogin(login -> login
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(salida -> salida
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}
