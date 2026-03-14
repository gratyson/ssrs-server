package com.gt.ssrs.reviewSession;

import com.gt.ssrs.auth.AuthenticatedUser;
import com.gt.ssrs.model.*;
import com.gt.ssrs.reviewSession.model.ClientReviewEvent;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/rest/review")
public class ReviewSessionController {

    private static final Logger log = LoggerFactory.getLogger(ReviewSessionController.class);

    private final ReviewSessionService reviewSessionService;
    private final ReviewEventProcessor reviewEventProcessor;
    private final ScheduledReviewService scheduledReviewService;

    public ReviewSessionController(ReviewSessionService reviewSessionService,
                                   ReviewEventProcessor reviewEventProcessor,
                                   ScheduledReviewService scheduledReviewService) {
        this.reviewSessionService = reviewSessionService;
        this.reviewEventProcessor = reviewEventProcessor;
        this.scheduledReviewService = scheduledReviewService;
    }

    @PostMapping("saveEvent")
    public void saveReviewEvent(@RequestBody ClientReviewEvent event,
                                @AuthenticatedUser String username) {
        this.reviewSessionService.saveReviewEvent(event, username, Instant.now());
    }

    @PostMapping("processManualEvent")
    public void processManualEvent(@RequestBody ClientReviewEvent event,
                                   @AuthenticatedUser String username) {
        this.reviewSessionService.recordManualEvent(event, username);
        reviewEventProcessor.processEvents(username, event.lexiconId());
    }

    @PostMapping("generateLearningSession")
    public List<List<WordReview>> generateLearningSession(@RequestBody GenerateLearningSessionRequest request,
                                                          @AuthenticatedUser String username,
                                                          HttpServletResponse response) {
        return reviewSessionService.generateLearningSession(request.lexiconId(), request.wordCnt(), username);
    }

    @PostMapping("generateReviewSession")
    public List<WordReview> generateReviewSession(@RequestBody GenerateReviewSessionRequest request,
                                                  @AuthenticatedUser String username,
                                                  HttpServletResponse response) {
        return reviewSessionService.generateReviewSession(
                request.lexiconId(),
                request.testRelationship != null && !request.testRelationship.isBlank() ? Optional.of(request.testRelationship) : Optional.empty(),
                request.cutoff,
                request.maxWordCnt(),
                username);
    }

    @PostMapping(value = "/adjustNextReviewTimes", consumes = "application/json", produces = "application/json")
    public void adjustNextReviewTimes(@RequestBody AdjustNextReviewTimesRequest adjustNextReviewTimesRequest,
                                      @AuthenticatedUser String username) {
        scheduledReviewService.adjustNextReviewTimes(
                adjustNextReviewTimesRequest.lexiconId,
                adjustNextReviewTimesRequest.adjustment,
                username);
    }

    @GetMapping(value = "/lexiconReviewSummary", produces = "application/json")
    public LexiconReviewSummary getLexiconReviewSummary(@RequestParam(value = "lexiconId") String lexiconId,
                                                        @RequestParam(value = "futureEventCutoff") Instant futureEventCutoff,
                                                        @AuthenticatedUser String username) {
        return reviewEventProcessor.getLexiconReviewSummary(lexiconId, username, futureEventCutoff);
    }

    private record GenerateLearningSessionRequest(String lexiconId, int wordCnt) { }
    private record GenerateReviewSessionRequest(String lexiconId, String testRelationship, int maxWordCnt, Optional<Instant> cutoff) { }
    private record AdjustNextReviewTimesRequest(String lexiconId, Duration adjustment) { }
}


