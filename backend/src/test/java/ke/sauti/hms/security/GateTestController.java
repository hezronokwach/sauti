package ke.sauti.hms.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** An ordinary API endpoint for tests, to show what a logged-in person can and cannot reach. */
@RestController
class GateTestController {

    @GetMapping("/api/test/ping")
    String ping() {
        return "pong";
    }
}
