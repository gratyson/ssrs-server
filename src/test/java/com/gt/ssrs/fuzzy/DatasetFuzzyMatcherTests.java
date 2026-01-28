package com.gt.ssrs.fuzzy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class DatasetFuzzyMatcherTests {

    private static final String target = "aaa";
    private static final List<String> dataset = List.of(
            "a",         // d = 2
            "aa",        // d = 1
            "aab",       // d = 1
            "aaba",      // d = 2
            "aaabbb",    // d = 3
            "aaabbbb",   // d = 4
            "aaabbbbb"); // d = 5

    private DatasetFuzzyMatcher datasetFuzzyMatcher;

    @BeforeEach
    public void before() {
        datasetFuzzyMatcher = new DatasetFuzzyMatcher(dataset);
    }

    @Test
    public void testLevenshteinDistance() {
        assertEquals(1, datasetFuzzyMatcher.levenshteinDistance("きりだす", "りだす", 6));
        assertEquals(1, datasetFuzzyMatcher.levenshteinDistance("ひとまく", "ひとまえ", 6));
        assertEquals(1, datasetFuzzyMatcher.levenshteinDistance("したじき", "したじ", 6));

        assertEquals(2, datasetFuzzyMatcher.levenshteinDistance("あながあく", "ながびく", 6));
        assertEquals(2, datasetFuzzyMatcher.levenshteinDistance("かごうぶつ", "ごうしつ", 6));
        assertEquals(2, datasetFuzzyMatcher.levenshteinDistance("げんせいりん", "さんせいけん", 6));

        assertEquals(3, datasetFuzzyMatcher.levenshteinDistance("きりだす", "きばん", 6));
        assertEquals(3, datasetFuzzyMatcher.levenshteinDistance("したじき", "しけい", 6));
        assertEquals(3, datasetFuzzyMatcher.levenshteinDistance("ねがったりかなったり", "あがったりさがったり", 6));

        assertEquals(4, datasetFuzzyMatcher.levenshteinDistance("さくにゅう", "きゅうしゅう", 6));
        assertEquals(4, datasetFuzzyMatcher.levenshteinDistance("すがすがしい", "かんすい", 6));
        assertEquals(4, datasetFuzzyMatcher.levenshteinDistance("ひとまく", "そこい", 6));

        assertEquals(5, datasetFuzzyMatcher.levenshteinDistance("さくにゅう", "ぶしつけ", 6));
        assertEquals(5, datasetFuzzyMatcher.levenshteinDistance("そのときはそのとき", "それはそうと", 6));
        assertEquals(5, datasetFuzzyMatcher.levenshteinDistance("にゅうしぼう", "かごうぶつ", 6));

        assertEquals(6, datasetFuzzyMatcher.levenshteinDistance("きりだす", "ひらしゃいん", 6));
        assertEquals(6, datasetFuzzyMatcher.levenshteinDistance("すがすがしい", "みにつける", 6));
        assertEquals(6, datasetFuzzyMatcher.levenshteinDistance("はたらきぶり", "ぜんどう", 6));
    }

    @Test
    public void testLevenshteinDistance_maxDistanceExceeded() {
        assertEquals(2, datasetFuzzyMatcher.levenshteinDistance("げんせいりん", "さんせいけん", 1));
        assertEquals(3, datasetFuzzyMatcher.levenshteinDistance("ねがったりかなったり", "あがったりさがったり", 2));
        assertEquals(4, datasetFuzzyMatcher.levenshteinDistance("ひとまく", "そこい", 3));
        assertEquals(5, datasetFuzzyMatcher.levenshteinDistance("にゅうしぼう", "かごうぶつ", 4));
        assertEquals(6, datasetFuzzyMatcher.levenshteinDistance("はたらきぶり", "ぜんどう", 5));
    }

    @Test
    public void testFindSimilarTo() {
        List<String> similar;

        similar = datasetFuzzyMatcher.findSimilarTo(target, 1, 4);
        assertEquals(1, similar.size());
        assertTrue(similar.get(0).equals("aa") || similar.get(0).equals("aab"));

        similar = datasetFuzzyMatcher.findSimilarTo(target, 2, 4);
        assertEquals(2, similar.size());
        assertTrue(similar.containsAll(List.of("aa", "aab")));

        similar = datasetFuzzyMatcher.findSimilarTo(target, 3, 4);
        assertEquals(3, similar.size());
        assertTrue(similar.containsAll(List.of("aa", "aab")));
        assertTrue(similar.contains("a") || similar.contains("aaba"));

        similar = datasetFuzzyMatcher.findSimilarTo(target, 4, 4);
        assertEquals(4, similar.size());
        assertTrue(similar.containsAll(List.of("aa", "aab", "a", "aaba")));

        similar = datasetFuzzyMatcher.findSimilarTo(target, 5, 4);
        assertEquals(5, similar.size());
        assertTrue(similar.containsAll(List.of("aa", "aab", "a", "aaba", "aaabbb")));

        similar = datasetFuzzyMatcher.findSimilarTo(target, 6, 4);
        assertEquals(6, similar.size());
        assertTrue(similar.containsAll(List.of("aa", "aab", "a", "aaba", "aaabbb", "aaabbbb")));
    }
}
