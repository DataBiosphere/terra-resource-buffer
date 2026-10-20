package bio.terra.buffer.app.filter;

import com.google.common.net.HttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds a restrictive Content-Security-Policy header to every response, including the Swagger UI
 * page and error pages. Inline scripts and styles are not allowed, so any HTML we serve must load
 * its scripts and styles from separate files on this origin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {
  public static final String HEADER_NAME = HttpHeaders.CONTENT_SECURITY_POLICY;

  // Swagger UI's CSS uses data: URIs for its icons, so img-src needs to allow data:.
  public static final String POLICY =
      String.join(
          "; ",
          "default-src 'self'",
          "script-src 'self'",
          "style-src 'self'",
          "img-src 'self' data:",
          "font-src 'self'",
          "connect-src 'self'",
          "object-src 'none'",
          "base-uri 'self'",
          "form-action 'self'",
          "frame-ancestors 'none'");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    response.setHeader(HEADER_NAME, POLICY);
    filterChain.doFilter(request, response);
  }

  // Also apply the header to error dispatches, so that error pages are covered.
  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }
}
