package com.gt.ssrs.app.aws;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "dev"})
class SsrsApplicationTests {

	@Test
	void contextLoads() { }
}
