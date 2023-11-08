package bio.terra.buffer.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.buffer.app.Main;
import bio.terra.buffer.generated.model.SystemStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@Tag("unit")
@ActiveProfiles({"test", "unit"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class, properties = "terra.common.prometheus.endpointEnabled=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class UnauthenticatedApiControllerTest {
  @Autowired private MockMvc mvc;

  @Test
  public void statusOK() throws Exception {
    MockHttpServletResponse response =
        this.mvc.perform(get("/status")).andExpect(status().isOk()).andReturn().getResponse();

    SystemStatus status =
        new ObjectMapper().readValue(response.getContentAsString(), SystemStatus.class);
    assertTrue(status.isOk());
    assertThat(status.getSystems(), Matchers.hasKey("postgres"));
    assertThat(status.getSystems(), Matchers.hasKey("stairway"));
  }
}
