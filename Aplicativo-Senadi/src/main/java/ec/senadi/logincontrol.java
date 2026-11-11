package ec.senadi;

import org.springframework.context.annotation.Bean;
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
    public UserDetailsService usuariosDePrueba(PasswordEncoder encoder) {
        var usuario = User.withUsername("prueba")
                .password(encoder.encode("123"))
                .roles("PRUEBA")
                .build();

        return new InMemoryUserDetailsManager(usuario);
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
                .anyRequest().authenticated()
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