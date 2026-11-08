package com.matheuspires.auction;

import org.springframework.boot.SpringApplication;

public class TestAuctionApplication {

	public static void main(String[] args) {
		SpringApplication.from(AuctionApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
