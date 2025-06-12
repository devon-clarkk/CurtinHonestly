package com.curtinhonestly.backend;

import org.springframework.boot.SpringApplication;

public class TestCurtinHonestlyApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CurtinHonestlyApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
