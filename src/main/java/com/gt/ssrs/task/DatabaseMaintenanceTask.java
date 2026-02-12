package com.gt.ssrs.task;

import com.gt.ssrs.reviewSession.ReviewEventDao;
import com.gt.ssrs.reviewSession.ScheduledReviewDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class DatabaseMaintenanceTask {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMaintenanceTask.class);

    private final ReviewEventDao reviewEventDao;
    private final ScheduledReviewDao scheduledReviewDao;
    private final int purgeEventsAfterDays;


    public DatabaseMaintenanceTask(ReviewEventDao reviewEventDao,
                                   ScheduledReviewDao scheduledReviewDao,
                                   @Value("${ssrs.maintenance.purgeAfterDays:7}") int purgeEventsAfterDays) {
        this.reviewEventDao = reviewEventDao;
        this.scheduledReviewDao = scheduledReviewDao;

        this.purgeEventsAfterDays = purgeEventsAfterDays;
    }

    @Scheduled(cron = "@daily")
    public void performDatabaseMaintenance() {
        // Review events has a foreign key pointing to scheduled reviews, so it needs to be deleted first
        purgeOldReviewEvents();
        purgeOldScheduledReviews();
    }

    private void purgeOldScheduledReviews() {
        // scheduled reviews are kept 1 day longer to avoid foreign key restraint issues
        Instant cutoff = Instant.now().minus(purgeEventsAfterDays + 1, ChronoUnit.DAYS);

        int rowsDeleted = scheduledReviewDao.purgeOldScheduledReviews(cutoff);

        log.info("Purged old scheduled reviews. {} row deleted.", rowsDeleted);
    }

    private void purgeOldReviewEvents() {
        Instant cutoff = Instant.now().minus(purgeEventsAfterDays, ChronoUnit.DAYS);

        int rowsDeleted = reviewEventDao.purgeOldReviewEvents(cutoff);

        log.info("Purged old review events. {} row deleted.", rowsDeleted);
    }
}
