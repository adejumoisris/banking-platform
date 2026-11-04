package com.rova.banking.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject browser cross-origin writes while retaining authenticated non-browser API clients. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class BrowserWriteGuard extends OncePerRequestFilter {
  private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path.equals("/") || path.equals("/index.html") || path.startsWith("/assets/")) {
      response.setHeader(
          "Content-Security-Policy",
          "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'");
      response.setHeader("Cache-Control", "no-store");
    }
    if (path.startsWith("/api/") && UNSAFE.contains(request.getMethod())) {
      String origin = request.getHeader("Origin");
      if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))
          || (origin != null && !sameOrigin(origin, request))) {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response
            .getWriter()
            .write(
                "{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Cross-origin browser writes are not allowed\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private boolean sameOrigin(String value, HttpServletRequest request) {
    try {
      URI origin = URI.create(value);
      int port =
          origin.getPort() >= 0 ? origin.getPort() : "https".equals(origin.getScheme()) ? 443 : 80;
      return origin.getUserInfo() == null
          && origin.getQuery() == null
          && origin.getFragment() == null
          && (origin.getPath() == null || origin.getPath().isEmpty())
          && origin.getHost() != null
          && origin.getScheme() != null
          && origin.getScheme().equalsIgnoreCase(request.getScheme())
          && origin.getHost().equalsIgnoreCase(request.getServerName())
          && port == request.getServerPort();
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
