package com.gt.ssrs.task;

import com.gt.ssrs.review.ReviewSessionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class DatabaseMaintenanceTask {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMaintenanceTask.class);

    private final ReviewSessionDao reviewSessionDao;
    private final int purgeEventsAfterDays;


    public DatabaseMaintenanceTask(ReviewSessionDao reviewSessionDao,
                                   @Value("${ssrs.maintenance.purgeAfterDays:7}") int purgeEventsAfterDays) {
        this.reviewSessionDao = reviewSessionDao;

        this.purgeEventsAfterDays = purgeEventsAfterDays;
    }

    @Scheduled(cron = "@daily")
    public void performDatabaseMaintenance() {
        purgeOldScheduledReviews();
        purgeOldReviewEvents();
    }

    private void purgeOldScheduledReviews() {
        Instant cutoff = Instant.now().minus(purgeEventsAfterDays, ChronoUnit.DAYS);

        int rowsDeleted = reviewSessionDao.purgeOldScheduledReviews(cutoff);

        log.info("Purged old scheduled reviews. {} row deleted.", rowsDeleted);
    }

    private void purgeOldReviewEvents() {
        Instant cutoff = Instant.now().minus(purgeEventsAfterDays, ChronoUnit.DAYS);

        int rowsDeleted = reviewSessionDao.purgeOldReviewEvents(cutoff);

        log.info("Purged old review events. {} row deleted.", rowsDeleted);
    }
}
