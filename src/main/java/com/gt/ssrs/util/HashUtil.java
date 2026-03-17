package com.gt.ssrs.util;

import com.gt.ssrs.language.WordElement;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.util.Collection;

public class HashUtil {
    private static final int SEED = 449386130; // Fixed seed so hash values stay consistent
    private static final XXHash32 hasher = XXHashFactory.fastestInstance().hash32();

    public static int computeHash(String input) {
        return hasher.hash(input.getBytes(), 0, input.length(), SEED);
    }

    public static int computeHash(Collection<String> inputs) {
        int hash = 0;

        for (String input : inputs) {
            if (input != null && !input.isEmpty()) {
                // Combine input hashes with bitwise XOR so that hash value is consistent even if element ordering changes
                hash = hash ^ HashUtil.computeHash(input);
            }
        }

        return hash;
    }
}