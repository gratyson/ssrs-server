package com.gt.ssrs.util;

import java.util.UUID;

// Util class to generate a short or stub UUIDs. Created with the intent of being used to add a unique element
// to existing id-based identifiers, specifically blobs files that may get cached by the client, where the
// number of sub-ids is expected to be limited.
public class ShortUUIDUtil {

    private static final int SHORT_UUID_LENGTH = 18;       // 8 bytes total, e.g. xxxxxxxx-xxxx-xxxx
    private static final int STUB_UUID_LENGTH = 8;         // 4 bytes total, e.g. xxxxxxxx

    public static String newShortUUID() {
        return UUID.randomUUID().toString().substring(0, SHORT_UUID_LENGTH);
    }

    public static String newStubUUID() {
        return UUID.randomUUID().toString().substring(0, STUB_UUID_LENGTH);
    }
}

