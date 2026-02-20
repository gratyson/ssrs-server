package com.gt.ssrs.util;

public class FileNameUtil {

    private static final String EXTENSION_SEPERATOR = ".";

    // Returns the extension, including the separator character, e.g. ".mp3", or empty string if no extension
    public static String getExtension(String fileName) {
        int index = fileName.lastIndexOf(EXTENSION_SEPERATOR);
        if (index > 0) {
            return fileName.substring(index);
        }
        return "";
    }

    public static String removeExtension(String fileName) {
        int index = fileName.lastIndexOf(EXTENSION_SEPERATOR);
        if (index > 0) {
            return fileName.substring(0, index);
        }
        return fileName;
    }
}
