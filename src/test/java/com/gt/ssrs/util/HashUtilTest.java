package com.gt.ssrs.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@ExtendWith(SpringExtension.class)
public class HashUtilTest {

    @Test
    public void testHash() {
        // basic sanity check to verify it gives the same value if called with the same string and different values with different strings
        Set<Integer> hashes = new HashSet<>();

        String prefix = "prefix";
        for(char c = 'a'; c <= 'z'; c++) {
            int hash = HashUtil.computeHash(prefix + c);
            assertEquals(hash, HashUtil.computeHash(prefix + c));

            hashes.add(hash);
        }

        assertEquals(26, hashes.size());
    }

    @Test
    public void testHash_OrderIndependent() {
        int hash = HashUtil.computeHash(List.of("Bob", "Tom", "Mary"));

        assertEquals(hash, HashUtil.computeHash(List.of("Bob", "Mary", "Tom")));
        assertEquals(hash, HashUtil.computeHash(List.of("Tom", "Mary", "Bob")));
        assertEquals(hash, HashUtil.computeHash(List.of("Tom", "Bob", "Mary")));
        assertEquals(hash, HashUtil.computeHash(List.of("Mary", "Bob", "Tom")));
        assertEquals(hash, HashUtil.computeHash(List.of("Mary", "Tom", "Bob")));

        assertNotEquals(hash, HashUtil.computeHash(List.of("Mary", "Tom")));
        assertNotEquals(hash, HashUtil.computeHash(List.of("Bob", "Tom")));
        assertNotEquals(hash, HashUtil.computeHash(List.of("Bob", "Mary")));

        assertNotEquals(hash, HashUtil.computeHash(List.of("Mary", "Tim", "Bob")));
        assertNotEquals(hash, HashUtil.computeHash(List.of("Mary", "Tom", "Bobby")));
    }
}
