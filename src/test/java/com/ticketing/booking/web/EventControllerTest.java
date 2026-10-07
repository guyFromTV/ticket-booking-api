package com.ticketing.booking.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.ticketing.booking.exception.NotFoundException;
import com.ticketing.booking.service.EventService;
import com.ticketing.booking.web.dto.CreateEventRequest;
import com.ticketing.booking.web.dto.CreateEventRequest.SectionLayout;
import com.ticketing.booking.web.dto.EventDetailResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice: request binding, validation and the error contract, with the
 * service mocked out. No database starts, so these run in milliseconds.
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @Test
    @DisplayName("Valid creation returns 201 with a Location header")
    void createReturnsCreated() throws Exception {
        Instant startsAt = Instant.now().plus(5, ChronoUnit.DAYS);
        given(eventService.createEvent(any()))
                .willReturn(new EventDetailResponse(
                        7L, "Gala", "Hall", startsAt, 100, 100, 0, 0, Instant.now()));

        CreateEventRequest request = new CreateEventRequest(
                "Gala", "Hall", startsAt, List.of(new SectionLayout("A", 10, 10, new BigDecimal("20.00"))));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/events/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.availableSeats").value(100));
    }

    @Test
    @DisplayName("Blank name and past date are reported per field as RFC 7807")
    void validationFailureListsFields() throws Exception {
        // Blank name, blank venue, a start date in the past and no sections.
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "",
                "venue", "",
                "startsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                "sections", List.of()));

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.venue").exists())
                .andExpect(jsonPath("$.fieldErrors.startsAt").exists())
                .andExpect(jsonPath("$.fieldErrors.sections").exists());
    }

    @Test
    @DisplayName("A seats-per-row of zero is rejected inside the nested section")
    void nestedSectionValidationApplies() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Gala",
                "venue", "Hall",
                "startsAt", Instant.now().plus(5, ChronoUnit.DAYS).toString(),
                "sections", List.of(Map.of("name", "A", "rows", 1, "seatsPerRow", 0, "price", "20.00"))));

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['sections[0].seatsPerRow']").exists());
    }

    @Test
    @DisplayName("A missing event becomes a 404 problem response")
    void missingEventReturnsProblemDetail() throws Exception {
        willThrow(NotFoundException.event(42L)).given(eventService).findById(42L);

        mockMvc.perform(get("/api/v1/events/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("No event with id 42"))
                .andExpect(jsonPath("$.type").value("https://api.ticketing.com/problems/not-found"));
    }
}
