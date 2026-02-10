package com.gt.ssrs.reviewHistory.model;

public enum LearnedStatus {
    ReadyToLearn(0),
    Learned(1);

    private int id;

    LearnedStatus(int id) {
        this.id = id;
    }

    public int id() {
         return id;
    }

    public static final LearnedStatus fromId(int id) {
        for (LearnedStatus learnedStatus : values()) {
            if (learnedStatus.id == id) {
                return learnedStatus;
            }
        }

        return null;
    }
}
