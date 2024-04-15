package com.gt.ssrs.model;

import java.util.List;

public record WordReview(long languageId,
                         Word word,
                         String scheduledEventId,
                         String testOn,
                         String promptWith,
                         String showAfterTest,
                         ReviewMode reviewMode,
                         ReviewType reviewType,
                         boolean recordResult,
                         int allowedTimeSec,
                         List<String> typingTestButtons,
                         List<String> multipleChoiceButtons) {

    public static WordReview withRecordResult(WordReview wordReview, boolean recordResult) {
        return new WordReview(wordReview.languageId(), wordReview.word, wordReview.scheduledEventId, wordReview.testOn, wordReview.promptWith,
                wordReview.showAfterTest, wordReview.reviewMode, wordReview.reviewType, recordResult, wordReview.allowedTimeSec,
                wordReview.typingTestButtons, wordReview.multipleChoiceButtons);
    }
}
