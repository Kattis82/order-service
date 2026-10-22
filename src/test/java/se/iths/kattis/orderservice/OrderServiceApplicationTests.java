package se.iths.kattis.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // använd ett testvärde så att testet inte behöver den riktiga hemligheten
        "internal.service-token=test-token"
})
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
