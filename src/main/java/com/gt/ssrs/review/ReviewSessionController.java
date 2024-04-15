package com.gt.ssrs.review;

import com.gt.ssrs.model.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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

    public ReviewSessionController(ReviewSessionService reviewSessionService, ReviewEventProcessor reviewEventProcessor) {
        this.reviewSessionService = reviewSessionService;
        this.reviewEventProcessor = reviewEventProcessor;
    }

    @PostMapping("saveEvent")
    public void saveReviewEvent(@RequestBody ReviewEvent event,
                                @AuthenticationPrincipal UserDetails userDetails) {
        this.reviewSessionService.saveReviewEvent(event, userDetails.getUsername(), Instant.now());
    }

    @PostMapping("generateLearningSession")
    public List<List<WordReview>> generateLearningSession(@RequestBody GenerateLearningSessionRequest request,
                                                          @AuthenticationPrincipal UserDetails userDetails,
                                                          HttpServletResponse response) {
            return reviewSessionService.generateLearningSession(request.lexiconId(), request.wordCnt(), userDetails.getUsername());
    }

    @PostMapping("generateReviewSession")
    public List<WordReview> generateReviewSession(@RequestBody GenerateReviewSessionRequest request,
                                                  @AuthenticationPrincipal UserDetails userDetails,
                                                  HttpServletResponse response) {
        return reviewSessionService.generateReviewSession(
                request.lexiconId(),
                request.testRelationship != null && !request.testRelationship.isBlank() ? Optional.of(request.testRelationship) : Optional.empty(),
                request.cutoff,
                request.maxWordCnt(),
                userDetails.getUsername());
    }

    @PostMapping(value = "/lexiconReviewHistoryBatch", consumes = "application/json", produces = "application/json")
    public List<LexiconReviewHistory> getLexiconWordHistoryBatch(@RequestBody GetLexiconReviewHistoryBatchRequest getLexiconWordHistoryBatchRequest,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return reviewSessionService.getLexiconHistoryBatch(getLexiconWordHistoryBatchRequest.lexiconId, userDetails.getUsername(), getLexiconWordHistoryBatchRequest.wordIds);
    }

    @PostMapping(value = "/saveLexiconReviewHistoryBatch", consumes = "application/json", produces = "application/json")
    public void saveLexiconWordHistoryBatch(@RequestBody List<LexiconReviewHistory> lexiconWordHistories,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        reviewSessionService.saveLexiconHistoryBatch(lexiconWordHistories, userDetails.getUsername());
    }

    @PostMapping(value = "/deleteLexiconReviewHistoryBatch", consumes = "application/json", produces = "application/json")
    public void deleteLexiconWordHistoryBatch(@RequestBody DeleteLexiconReviewHistoryRequest deleteLexiconReviewHistoryRequest,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        reviewSessionService.deleteLexiconHistoryBatch(
                deleteLexiconReviewHistoryRequest.lexiconId,
                deleteLexiconReviewHistoryRequest.wordIds,
                userDetails.getUsername());
    }

    @PostMapping(value = "/adjustNextReviewTimes", consumes = "application/json", produces = "application/json")
    public void adjustNextReviewTimes(@RequestBody AdjustNextReviewTimesRequest adjustNextReviewTimesRequest,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        reviewSessionService.adjustNextReviewTimes(
                adjustNextReviewTimesRequest.lexiconId,
                adjustNextReviewTimesRequest.adjustment,
                userDetails.getUsername());
    }

    @GetMapping(value = "/lexiconReviewSummary", produces = "application/json")
    public LexiconReviewSummary getLexiconReviewSummary(@RequestParam(value = "lexiconId") String lexiconId,
                                                        @RequestParam(value = "futureEventCutoff") Instant futureEventCutoff,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return reviewEventProcessor.getLexiconReviewSummary(lexiconId, userDetails.getUsername(), futureEventCutoff);
    }

    private record GenerateLearningSessionRequest(String lexiconId, int wordCnt) { }
    private record GenerateReviewSessionRequest(String lexiconId, String testRelationship, int maxWordCnt, Optional<Instant> cutoff) { }
    private record GetLexiconReviewHistoryBatchRequest(String lexiconId, Collection<String> wordIds) { }
    private record DeleteLexiconReviewHistoryRequest(String lexiconId, Collection<String> wordIds) { }
    private record AdjustNextReviewTimesRequest(String lexiconId, Duration adjustment) { }
}


