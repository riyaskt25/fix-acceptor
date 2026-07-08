package com.demo.fix.acceptor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "fix.enabled=false")
class FixAcceptorApplicationTests {

	@Test
	void contextLoads() {
	}

}
