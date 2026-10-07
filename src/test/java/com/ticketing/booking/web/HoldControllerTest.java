package com.ticketing.booking.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.ticketing.booking.exception.SeatsUnavailableException;
import com.ticketing.booking.service.HoldService;
import com.ticketing.booking.web.dto.CreateHoldRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HoldController.class)
class HoldControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HoldService holdService;

    @Test
    @DisplayName("A taken seat returns 409 naming the seats to re-pick")
    void takenSeatsReturnConflictWithSeatLabels() throws Exception {
        given(holdService.createHold(eq(1L), any()))
                .willThrow(new SeatsUnavailableException(List.of("Stalls-A-1", "Stalls-A-2")));

        String body = objectMapper.writeValueAsString(new CreateHoldRequest("buyer@example.com", List.of(1L, 2L)));

        mockMvc.perform(post("/api/v1/events/1/holds").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Seats unavailable"))
                .andExpect(jsonPath("$.unavailableSeats[0]").value("Stalls-A-1"))
                .andExpect(jsonPath("$.unavailableSeats[1]").value("Stalls-A-2"));
    }

    @Test
    @DisplayName("A malformed email is rejected before reaching the service")
    void malformedEmailIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("customerEmail", "not-an-email", "seatIds", List.of(1L)));

        mockMvc.perform(post("/api/v1/events/1/holds").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerEmail").exists());
    }

    @Test
    @DisplayName("Holding more than ten seats at once is rejected")
    void tooManySeatsRejected() throws Exception {
        List<Long> eleven = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        String body = objectMapper.writeValueAsString(Map.of("customerEmail", "b@example.com", "seatIds", eleven));

        mockMvc.perform(post("/api/v1/events/1/holds").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.seatIds").value("a single hold may cover at most 10 seats"));
    }

    @Test
    @DisplayName("An empty seat list is rejected")
    void emptySeatListRejected() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("customerEmail", "b@example.com", "seatIds", List.of()));

        mockMvc.perform(post("/api/v1/events/1/holds").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.seatIds").exists());
    }
}
