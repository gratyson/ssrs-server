package com.gt.ssrs.lexicon;

import com.gt.ssrs.auth.AuthenticatedUser;
import com.gt.ssrs.delete.DeletionService;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import com.gt.ssrs.reviewSession.ReviewEventProcessor;
import com.gt.ssrs.reviewSession.ScheduledReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.gt.ssrs.word.WordService;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/rest/lexicon")
public class LexiconController {

    private static final Logger log = LoggerFactory.getLogger(LexiconController.class);

    private static final int HTTP_STATUS_UNPROCESSABLE_CONTENT = 422;

    private final LexiconService lexiconService;
    private final WordService wordService;
    private final WordReviewHistoryService wordReviewHistoryService;
    private final DeletionService deletionService;
    private final ScheduledReviewService scheduledReviewService;
    private final ReviewEventProcessor reviewEventProcessor;

    @Autowired
    public LexiconController(LexiconService lexiconService,
                             WordService wordService,
                             WordReviewHistoryService wordReviewHistoryService,
                             DeletionService deletionService,
                             ScheduledReviewService scheduledReviewService,
                             ReviewEventProcessor reviewEventProcessor) {
        this.lexiconService = lexiconService;
        this.wordService = wordService;
        this.wordReviewHistoryService = wordReviewHistoryService;
        this.deletionService = deletionService;
        this.scheduledReviewService = scheduledReviewService;
        this.reviewEventProcessor = reviewEventProcessor;
    }

    @GetMapping(value = "/allLexiconMetadata", produces = "application/json")
    public List<LexiconMetadata> getAllLexiconMetadata(@AuthenticatedUser String username, HttpServletRequest request) {
        return lexiconService.getAllLexiconMetadata(username);
    }

    @GetMapping(value = "/allLexiconMetadataAndScheduledCounts", produces = "application/json")
    public List<LexiconMetadataAndScheduledCounts> getAllLexiconMetadataAndScheduledCounts(@RequestParam(value = "cutoff") Optional<Instant> cutoffInstant,
                                                                                           @AuthenticatedUser String username,
                                                                                           HttpServletResponse response) {
        List<LexiconMetadataAndScheduledCounts> lexiconMetadataAndScheduledCounts = new ArrayList<>();

        for(LexiconMetadata lexiconMetadata : lexiconService.getAllLexiconMetadata(username)) {
            reviewEventProcessor.processEvents(username, lexiconMetadata.id());
            lexiconMetadataAndScheduledCounts.add(new LexiconMetadataAndScheduledCounts(
                    lexiconMetadata,
                    scheduledReviewService.getScheduledReviewCounts(username, lexiconMetadata.id(), cutoffInstant),
                    wordReviewHistoryService.hasWordsToLearn(lexiconMetadata.id(), username)));
        }

        return lexiconMetadataAndScheduledCounts;
    }

    @GetMapping(value = "/lexiconMetadata", produces = "application/json")
    public LexiconMetadata getLexiconMetadata(@RequestParam(value = "id") String lexiconId) {
        return lexiconService.getLexiconMetadata(lexiconId);
    }

    @PostMapping(value = "/saveLexiconMetadata", produces = "application/json")
    public LexiconMetadata saveLexiconMetadata(@RequestPart("lexicon") LexiconMetadata lexicon,
                                               @RequestPart(value = "file") MultipartFile file,
                                               @AuthenticatedUser String username,
                                               HttpServletResponse response) throws IOException {
        LexiconMetadata newLexiconMetadata = lexiconService.saveLexiconMetadata(username, lexicon, file);
        if (newLexiconMetadata != null) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return newLexiconMetadata;
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return null;
        }
    }

    @PostMapping(value = "/deleteLexicon")
    public void deleteLexicon(@RequestBody String lexiconId,
                              @AuthenticatedUser String username) {
        deletionService.deleteLexicon(username, lexiconId);
    }

    @GetMapping(value = "/word", produces = "application/json")
    public Word getWord(@RequestParam(value = "id") String wordId) {
        return wordService.loadWord(wordId);
    }

    @PostMapping(value = "/updateWord", consumes = "application/json", produces = "application/json")
    public Word updateWord(@RequestBody Word word,
                           @AuthenticatedUser String username,
                           HttpServletResponse response) {
        Word updatedWord = wordService.updateWord(word, username);
        if (updatedWord != null) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return updatedWord;
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return null;
        }
    }

    @PostMapping(value = "/deleteWords", consumes = "application/json")
    public void deleteWords(@RequestBody DeleteWordsRequest deleteWordsRequest,
                            @AuthenticatedUser String username) {
        deletionService.deleteWords(username, deleteWordsRequest.lexiconId(), deleteWordsRequest.wordIds());
    }

    @PostMapping(value = "/saveWords", consumes = "application/json", produces = "application/json")
    public List<Word> saveWords(@RequestBody SaveWordsRequest saveWordsRequest,
                                @AuthenticatedUser String username,
                                HttpServletResponse response) {
        List<Word> savedWords = wordService.saveWords(saveWordsRequest.words(), saveWordsRequest.lexiconId(), username);

        response.setStatus(HttpServletResponse.SC_ACCEPTED);
        return savedWords;
    }

    @PostMapping(value = "/lexiconWords", consumes = "application/json", produces = "application/json")
    public List<Word> getLexiconWordsBatch(@RequestBody GetLexiconWordsBatchRequest getLexiconWordsBatchRequest,
                                           @AuthenticatedUser String username) {
        return wordService.getLexiconWordsBatch(
                getLexiconWordsBatchRequest.lexiconId(),
                username,
                getLexiconWordsBatchRequest.count(),
                getLexiconWordsBatchRequest.offset(),
                getLexiconWordsBatchRequest.lastWord(),
                getLexiconWordsBatchRequest.filters());
    }

    private record SaveWordsRequest(String lexiconId, List<Word> words) { }
    private record DeleteWordsRequest(String lexiconId, List<String> wordIds) { }
    private record GetLexiconWordsBatchRequest(String lexiconId, int count, int offset, Word lastWord, WordFilterOptions filters) { }
    private record LexiconMetadataAndScheduledCounts(LexiconMetadata lexiconMetadata, Map<String, Integer> scheduledReviewCounts, boolean hasWordsToLearn) { }

}
