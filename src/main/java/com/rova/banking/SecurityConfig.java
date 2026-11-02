package com.rova.banking;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean @Profile("!local") SecurityFilterChain production(HttpSecurity http) throws Exception {
  JwtGrantedAuthoritiesConverter roles=new JwtGrantedAuthoritiesConverter(); roles.setAuthoritiesClaimName("roles"); roles.setAuthorityPrefix("ROLE_");
  JwtAuthenticationConverter converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(roles);
  return base(http).oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))).build();
 }
 static HttpSecurity base(HttpSecurity http) throws Exception {
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**").permitAll().anyRequest().authenticated());
 }
}
