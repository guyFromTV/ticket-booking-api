package com.ticketing.booking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ticket Booking API")
                .version("v1")
                .description("""
                        Seat inventory, time-limited holds and confirmed bookings.

                        Booking is a two-step flow: hold the seats, then confirm the hold.
                        Holds expire automatically, and concurrent requests for the same
                        seat are serialised with a pessimistic row lock so a seat can
                        never be sold twice."""));
    }
}
