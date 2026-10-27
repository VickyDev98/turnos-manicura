package com.victoria.turnos_manicura;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Perfil "test": usa la base H2 en memoria, no hace falta MySQL
@SpringBootTest
@ActiveProfiles("test")
class TurnosManicuraApplicationTests {

	@Test
	void contextLoads() {
	}

}
