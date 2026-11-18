package ke.sauti;

import org.springframework.boot.SpringApplication;

public class TestSautiApplication {

    public static void main(String[] args) {
        SpringApplication.from(SautiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
