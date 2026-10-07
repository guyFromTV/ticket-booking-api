package com.ticketing.booking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auditing is enabled here rather than on the application class on purpose.
 * {@code @EnableJpaAuditing} needs JPA infrastructure, and sliced web tests
 * ({@code @WebMvcTest}) start without it -- leaving the annotation on the main
 * class would make every one of those tests fail to start a context.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
