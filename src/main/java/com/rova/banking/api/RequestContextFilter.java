package com.rova.banking.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("bankingCorrelationFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader("X-Request-ID");
    String requestId =
        supplied != null && supplied.matches("[A-Za-z0-9-]{1,64}")
            ? supplied
            : UUID.randomUUID().toString();
    response.setHeader("X-Request-ID", requestId);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
      chain.doFilter(request, response);
    }
  }
}
