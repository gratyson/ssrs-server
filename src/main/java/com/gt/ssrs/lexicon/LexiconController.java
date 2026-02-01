package com.gt.ssrs.lexicon;

import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import com.gt.ssrs.review.ReviewEventProcessor;
import com.gt.ssrs.review.ReviewSessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/rest/lexicon")
public class LexiconController {

    private static final Logger log = LoggerFactory.getLogger(LexiconController.class);

    private static final int HTTP_STATUS_UNPROCESSABLE_CONTENT = 422;
    private static final String MIN_INSTANT = "1900-01-01T00:00:00Z";
    private static final Instant NULL_INSTANT = null;

    private final LexiconService lexiconService;
    private final ReviewSessionService reviewSessionService;
    private final ReviewEventProcessor reviewEventProcessor;

    @Autowired
    public LexiconController(LexiconService lexiconService, ReviewSessionService reviewSessionService, ReviewEventProcessor reviewEventProcessor) {
        this.lexiconService = lexiconService;
        this.reviewSessionService = reviewSessionService;
        this.reviewEventProcessor = reviewEventProcessor;
    }

    @GetMapping(value = "/allLexiconMetadata", produces = "application/json")
    public List<LexiconMetadata> getAllLexiconMetadata(@AuthenticationPrincipal UserDetails userDetails) {
        return lexiconService.loadAllLexiconMetadata(userDetails.getUsername());
    }

    @GetMapping(value = "/allLexiconMetadataAndScheduledCounts", produces = "application/json")
    public List<LexiconMetadataAndScheduledCounts> getAllLexiconMetadataAndScheduledCounts(@RequestParam(value = "cutoff") Optional<Instant> cutoffInstant,
                                                                                           @AuthenticationPrincipal UserDetails userDetails,
                                                                                           HttpServletResponse response) {
        List<LexiconMetadataAndScheduledCounts> lexiconMetadataAndScheduledCounts = new ArrayList<>();

        for(LexiconMetadata lexiconMetadata : lexiconService.loadAllLexiconMetadata(userDetails.getUsername())) {
            reviewEventProcessor.processEvents(userDetails.getUsername(), lexiconMetadata.id());
            lexiconMetadataAndScheduledCounts.add(new LexiconMetadataAndScheduledCounts(
                    lexiconMetadata,
                    reviewSessionService.getScheduledReviewCounts(userDetails.getUsername(), lexiconMetadata.id(), cutoffInstant),
                    lexiconService.getLexiconWordsBatch(lexiconMetadata.id(), userDetails.getUsername(), 1, 0, new WordFilterOptions(Map.of(), null, false, null)).size() > 0));
        }

        return lexiconMetadataAndScheduledCounts;
    }

    @GetMapping(value = "/lexiconMetadata", produces = "application/json")
    public LexiconMetadata getLexiconMetadata(@RequestParam(value = "id") String lexiconId) {
        return lexiconService.getLexiconMetadata(lexiconId);
    }

    @PutMapping(value = "/saveLexiconMetadata", produces = "application/json")
    public LexiconMetadata saveLexiconMetadata(@RequestPart("lexicon") LexiconMetadata lexicon,
                                               @RequestPart(value = "file") MultipartFile file,
                                               @AuthenticationPrincipal UserDetails userDetails,
                                               HttpServletResponse response) throws IOException {
        LexiconMetadata newLexiconMetadata = lexiconService.saveLexiconMetadata(userDetails.getUsername(), lexicon, file);
        if (newLexiconMetadata != null) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return newLexiconMetadata;
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return null;
        }
    }

    @PostMapping(value = "/deleteLexicon")
    public boolean deleteLexicon(@RequestBody String lexiconId,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        return lexiconService.deleteEntireLexicon(lexiconId, userDetails.getUsername());
    }

    @GetMapping(value = "/word", produces = "application/json")
    public Word getWord(@RequestParam(value = "id") String wordId) {
        return lexiconService.loadWord(wordId);
    }

    @PostMapping(value = "/updateWord", consumes = "application/json", produces = "application/json")
    public Word updateWord(@RequestBody Word word,
                           @AuthenticationPrincipal UserDetails userDetails,
                           HttpServletResponse response) {
        Word updatedWord = lexiconService.updateWord(word, userDetails.getUsername());
        if (word != null) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return word;
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return null;
        }
    }

    @PostMapping(value = "/deleteWords", consumes = "application/json")
    public void deleteWords(@RequestBody DeleteWordsRequest deleteWordsRequest,
                           @AuthenticationPrincipal UserDetails userDetails) {
        lexiconService.deleteWords(deleteWordsRequest.lexiconId(), deleteWordsRequest.wordIds(), userDetails.getUsername());
    }

    @PutMapping(value = "/saveWords", consumes = "application/json", produces = "application/json")
    public List<Word> saveWords(@RequestBody SaveWordsRequest saveWordsRequest,
                                @AuthenticationPrincipal UserDetails userDetails,
                                HttpServletResponse response) {
        List<Word> savedWords = lexiconService.saveWords(saveWordsRequest.words(), saveWordsRequest.lexiconId(), userDetails.getUsername());

        response.setStatus(HttpServletResponse.SC_ACCEPTED);
        return savedWords;
    }

    @PostMapping(value = "/lexiconWords", consumes = "application/json", produces = "application/json")
    public List<Word> getLexiconWordsBatch(@RequestBody GetLexiconWordsBatchRequest getLexiconWordsBatchRequest,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        return lexiconService.getLexiconWordsBatch(
                getLexiconWordsBatchRequest.lexiconId(),
                userDetails.getUsername(),
                getLexiconWordsBatchRequest.count(),
                getLexiconWordsBatchRequest.offset(),
                getLexiconWordsBatchRequest.filters());
    }

    private record SaveWordsRequest(String lexiconId, List<Word> words) { }
    private record DeleteWordsRequest(String lexiconId, List<String> wordIds) { }
    private record GetLexiconWordsBatchRequest(String lexiconId, int count, int offset, WordFilterOptions filters) { }
    private record LexiconMetadataAndScheduledCounts(LexiconMetadata lexiconMetadata, Map<String, Integer> scheduledReviewCounts, boolean hasWordsToLearn) { }

}
