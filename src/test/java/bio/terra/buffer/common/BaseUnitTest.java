package bio.terra.buffer.common;

import bio.terra.buffer.app.Main;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("unit")
@ActiveProfiles({"test", "unit", "human-readable-logging"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Main.class, properties = "terra.common.prometheus.endpointEnabled=false")
public class BaseUnitTest {}
