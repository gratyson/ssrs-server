package com.gt.ssrs.util;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

public class ListUtil {

    public static <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        if (partitionSize < 1) {
            throw new InvalidParameterException("List partition size must be greater than 0");
        }

        if (list == null || list.size() == 0) {
            return List.of(List.of());
        }

        List<List<T>> listOfLists = new ArrayList<>();

        for(int offset = 0; offset < list.size(); offset += partitionSize) {
            listOfLists.add(list.subList(offset, Math.min(list.size(), offset + partitionSize)));
        }

        return listOfLists;
    }
}
