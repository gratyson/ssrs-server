package com.gt.ssrs.language;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(SpringExtension.class)
public class TestRelationshipTests {

    @Test
    public void testGetTestRelationshipById() {
        for (TestRelationship testRelationship : TestRelationship.values()) {
            assertEquals(testRelationship, TestRelationship.getTestRelationshipById(testRelationship.getId()));
        }
    }

    @Test
    public void testFallbackValidity() {
        for (TestRelationship testRelationship : TestRelationship.values()) {
            if (testRelationship.getFallback() != null) {
                Set<TestRelationship> seenRelationships = new HashSet<>();
                seenRelationships.add(testRelationship);

                TestRelationship currentRelationship = testRelationship;
                while (currentRelationship.getFallback() != null) {
                    assertFalse(seenRelationships.contains(currentRelationship.getFallback()));
                    currentRelationship = currentRelationship.getFallback();
                }
            }
        }
    }
}
