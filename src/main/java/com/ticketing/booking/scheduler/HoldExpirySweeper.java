package com.ticketing.booking.scheduler;

import com.ticketing.booking.service.HoldService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Returns timed-out holds to the available pool.
 *
 * <p>The sweeper is a cleanup job, not a correctness guarantee: confirmation
 * already refuses an expired hold on its own. That split means a late or failed
 * run can never let an expired hold convert into a booking.
 */
@Component
public class HoldExpirySweeper {

    private final HoldService holdService;

    public HoldExpirySweeper(HoldService holdService) {
        this.holdService = holdService;
    }

    @Scheduled(fixedDelayString = "${booking.sweeper-interval}")
    public void sweep() {
        holdService.expireStaleHolds();
    }
}
