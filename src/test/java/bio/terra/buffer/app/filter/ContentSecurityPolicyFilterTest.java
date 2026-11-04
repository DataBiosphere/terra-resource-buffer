package bio.terra.buffer.app.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.buffer.common.BaseUnitTest;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class ContentSecurityPolicyFilterTest extends BaseUnitTest {
  private static final Pattern INLINE_STYLE =
      Pattern.compile("<style\\b", Pattern.CASE_INSENSITIVE);
  // A <script> tag without a src attribute holds inline code.
  private static final Pattern INLINE_SCRIPT =
      Pattern.compile("<script\\b(?![^>]*\\bsrc\\s*=)[^>]*>", Pattern.CASE_INSENSITIVE);

  @Autowired private MockMvc mvc;

  @Test
  public void swaggerPageHasCsp() throws Exception {
    mvc.perform(get("/swagger-ui.html"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    ContentSecurityPolicyFilter.HEADER_NAME, ContentSecurityPolicyFilter.POLICY));
  }

  @Test
  public void swaggerPageHasNoInlineScriptOrStyle() throws Exception {
    String page =
        mvc.perform(get("/swagger-ui.html"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // The CSP blocks inline code, so the page must load its scripts and styles from files.
    assertFalse(INLINE_STYLE.matcher(page).find(), "swagger-ui.html has an inline <style> block");
    assertFalse(INLINE_SCRIPT.matcher(page).find(), "swagger-ui.html has an inline <script> block");
  }

  @Test
  public void swaggerScriptAndStyleFilesAreServed() throws Exception {
    for (String path : List.of("/swagger-ui-overrides.css", "/swagger-initializer.js")) {
      mvc.perform(get(path))
          .andExpect(status().isOk())
          .andExpect(
              header()
                  .string(
                      ContentSecurityPolicyFilter.HEADER_NAME, ContentSecurityPolicyFilter.POLICY));
    }
  }

  @Test
  public void notFoundPageHasCsp() throws Exception {
    mvc.perform(get("/does-not-exist").accept(MediaType.TEXT_HTML))
        .andExpect(status().isNotFound())
        .andExpect(
            header()
                .string(
                    ContentSecurityPolicyFilter.HEADER_NAME, ContentSecurityPolicyFilter.POLICY));
  }

  @Test
  public void unauthenticatedApiErrorHasCsp() throws Exception {
    // No bearer token, so this request is rejected.
    mvc.perform(get("/api/pool/v1/some-pool"))
        .andExpect(
            header()
                .string(
                    ContentSecurityPolicyFilter.HEADER_NAME, ContentSecurityPolicyFilter.POLICY));
  }
}
