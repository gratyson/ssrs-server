package com.gt.ssrs.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


@ExtendWith(SpringExtension.class)
public class ListUtilTest {

    @Test
    public void testPartitionList() {
        List<Integer> fullList = IntStream.range(0, 55).boxed().toList();

        List<List<Integer>> subListList = ListUtil.partitionList(fullList, 25);

        assertEquals(3, subListList.size());
        assertEquals(IntStream.range(0, 25).boxed().toList(), subListList.get(0));
        assertEquals(IntStream.range(25, 50).boxed().toList(), subListList.get(1));
        assertEquals(IntStream.range(50, 55).boxed().toList(), subListList.get(2));
    }

    @Test
    public void testPartitionList_ShorterThanPartition() {
        List<Integer> fullList = IntStream.range(0, 15).boxed().toList();

        List<List<Integer>> subListList = ListUtil.partitionList(fullList, 25);

        assertEquals(1, subListList.size());
        assertEquals(IntStream.range(0, 15).boxed().toList(), subListList.get(0));
    }

    @Test
    public void testPartitionList_EmptyList() {
        assertEquals(List.of(List.of()), ListUtil.partitionList(List.of(), 25));
    }

    @Test
    public void testPartitionList_NullList() {
        assertEquals(List.of(List.of()), ListUtil.partitionList(null, 25));
    }

    @Test
    public void testPartitionList_ZeroLengthPartition() {
        try {
            ListUtil.partitionList(IntStream.range(0, 55).boxed().toList(), 0);
        } catch (InvalidParameterException e) {
            return;
        }

        fail("Expected InvalidParameterException");
    }
}
