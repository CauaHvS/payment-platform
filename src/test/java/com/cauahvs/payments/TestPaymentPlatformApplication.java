package com.cauahvs.payments;

import org.springframework.boot.SpringApplication;

public class TestPaymentPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
