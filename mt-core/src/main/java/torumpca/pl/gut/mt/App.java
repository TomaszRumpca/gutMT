package torumpca.pl.gut.mt;

import com.google.common.collect.ImmutableList;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Configuration
    @Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
    protected static class SecurityConfiguration extends WebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            final CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(ImmutableList.of("*"));
            configuration.setAllowedMethods(ImmutableList.of("HEAD",
                    "GET", "POST", "PUT", "DELETE", "PATCH"));
//             setAllowCredentials(true) is important, otherwise:
//             The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' when the request's credentials mode is 'include'.
            configuration.setAllowCredentials(true);
//             setAllowedHeaders is important! Without it, OPTIONS preflight request
//             will fail with 403 Invalid CORS request
            configuration.setAllowedHeaders(ImmutableList.of("Authorization", "Cache-Control", "Content-Type"));
            final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);

            http
                    .httpBasic()
                    .and()
                    .cors()
                    .configurationSource(source)
                    .and()
                    .authorizeRequests()
                    .antMatchers("/api/forecast/meta", "/api/solve", "/user", "/users/*", "seaport")
                    .permitAll()
                    .anyRequest()
                    .authenticated();
        }
    }


//    @Configuration
//    public class WebConfig extends WebMvcConfigurerAdapter {
//
//        @Override
//        public void addCorsMappings(CorsRegistry registry) {
//            registry.addMapping("/**")
//                    .allowedMethods("HEAD", "GET", "PUT", "POST", "DELETE", "PATCH")
//                    .allowedOrigins("*");
//        }
//    }
}
