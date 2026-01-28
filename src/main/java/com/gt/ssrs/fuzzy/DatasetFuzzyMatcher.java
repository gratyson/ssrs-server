package com.gt.ssrs.fuzzy;

import java.util.*;
import java.util.stream.Collectors;

public class DatasetFuzzyMatcher {

    private List<String> dataset;

    private List<Integer> v0 = new ArrayList<>();
    private List<Integer> v1 = new ArrayList<>();

    public DatasetFuzzyMatcher(Collection<String> dataSet1) {
        this.dataset = dataSet1.stream().distinct().collect(Collectors.toUnmodifiableList());
    }

    public List<String> findSimilarTo(String target, int count, int maxDistance) {
        return dataset.stream()
                .filter(datum -> !datum.equals(target))  // don't include the target as similar if it's in the dataset
                .map(datum -> new DistanceAndDatum(levenshteinDistance(target, datum, maxDistance), datum))
                .sorted(Comparator.comparingInt(l -> l.distance))
                .limit(count)
                .map(dnd -> dnd.datum)
                .collect(Collectors.toUnmodifiableList());
    }

    int levenshteinDistance(String left, String right, int maxDistance) {
        initalizeLists(right.length() + 1);

        for (int i = 0; i < left.length(); i++) {
            v1.set(0, i + 1);

            for (int j = 0; j < right.length(); j++) {
                int delCost = v0.get(j + 1) + 1;
                int insertCost = v1.get(j) + 1;
                int subCost = left.charAt(i) == right.charAt(j) ? v0.get(j) : v0.get(j) + 1;

                int curDistance = Integer.min(Integer.min(delCost, insertCost), subCost);

                v1.set(j + 1, curDistance);
            }

            if (Collections.min(v1) > maxDistance) {
                return maxDistance + 1;
            }

            swapLists();
        }

        return v0.get(right.length());
    }

    private void initalizeLists(int size) {
        v0.clear();
        v1.clear();
        for (int i = 0; i < size; i++) {
            v0.add(i);
            v1.add(0);
        }
    }

    private void swapLists() {
        List<Integer> temp = v0;
        v0 = v1;
        v1 = temp;
    }

    private record DistanceAndDatum(int distance, String datum) {  }
}
