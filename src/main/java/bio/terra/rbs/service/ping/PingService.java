package bio.terra.rbs.service.ping;

import bio.terra.rbs.service.ping.exception.BadPingException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// TODO: rbs sample service component that handles the ping request

@Component
public class PingService {
  public String computePing(String message) {
    if (StringUtils.isEmpty(message)) {
      throw new BadPingException("No message to ping");
    }
    return "pong: " + message + "\n";
  }
}
