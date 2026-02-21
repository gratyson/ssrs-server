package com.gt.ssrs.reviewHistory;

import com.gt.ssrs.delete.DeletionService;
import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.converter.ClientWordReviewHistoryConverter;
import com.gt.ssrs.reviewHistory.model.ClientWordReviewHistory;
import com.gt.ssrs.reviewSession.ScheduledReviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rest/history")
public class WordReviewHistoryController {

    private final WordReviewHistoryService wordReviewHistoryService;
    private final ScheduledReviewService scheduledReviewService;
    private final DeletionService deletionService;

    public WordReviewHistoryController(WordReviewHistoryService wordReviewHistoryService, ScheduledReviewService scheduledReviewService, DeletionService deletionService) {
        this.wordReviewHistoryService = wordReviewHistoryService;
        this.scheduledReviewService = scheduledReviewService;
        this.deletionService = deletionService;
    }

    @PostMapping(value = "/getWordReviewHistoryBatch", consumes = "application/json", produces = "application/json")
    public List<ClientWordReviewHistory> getLexiconWordHistoryBatch(@RequestBody GetLexiconReviewHistoryBatchRequest getLexiconWordHistoryBatchRequest,
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        return wordReviewHistoryService.getWordReviewHistory(getLexiconWordHistoryBatchRequest.lexiconId, userDetails.getUsername(), getLexiconWordHistoryBatchRequest.wordIds)
                .stream()
                .map(wordReviewHistory -> ClientWordReviewHistoryConverter.convertWordReviewHistory(wordReviewHistory))
                .collect(Collectors.toUnmodifiableList());
    }

    @PostMapping(value = "/saveWordReviewHistoryBatch", consumes = "application/json", produces = "application/json")
    public void saveLexiconWordHistoryBatch(@RequestBody List<ClientWordReviewHistory> lexiconWordHistories,
                                            @AuthenticationPrincipal UserDetails userDetails) {

        List<WordReviewHistory> wordReviewHistoryToSave = lexiconWordHistories
                .stream()
                .map(clientWordReviewHistory -> ClientWordReviewHistoryConverter.convertClientWordReviewHistory(userDetails.getUsername(), clientWordReviewHistory))
                .collect(Collectors.toUnmodifiableList());

        List<WordReviewHistory> updatedHistory = wordReviewHistoryService.updateWordReviewHistoryBatch(userDetails.getUsername(), wordReviewHistoryToSave);
        scheduledReviewService.scheduleReviewsForHistory(userDetails.getUsername(), updatedHistory);
    }

    @PostMapping(value = "/resetLearningHistory", consumes = "application/json", produces = "application/json")
    public void resetLearningHistory(@RequestBody ResetLearningHistoryRequest deleteLexiconReviewHistoryRequest,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        deletionService.deleteUserWordTestHistory(userDetails.getUsername(), deleteLexiconReviewHistoryRequest.lexiconId, deleteLexiconReviewHistoryRequest.wordIds);

        wordReviewHistoryService.resetLearningHistory(
                deleteLexiconReviewHistoryRequest.lexiconId,
                userDetails.getUsername(),
                deleteLexiconReviewHistoryRequest.wordIds);
    }

    private record GetLexiconReviewHistoryBatchRequest(String lexiconId, Collection<String> wordIds) { }
    private record ResetLearningHistoryRequest(String lexiconId, Collection<String> wordIds) { }
}
