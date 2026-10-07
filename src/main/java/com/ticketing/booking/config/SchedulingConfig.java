package com.ticketing.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Kept out of the application class so sliced tests do not start the schedulers. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
