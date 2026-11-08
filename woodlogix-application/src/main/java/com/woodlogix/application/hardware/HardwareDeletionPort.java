package com.woodlogix.application.hardware;

public interface HardwareDeletionPort {
    boolean exists(String id);

    boolean isReferencedByDraft(String id);

    void deleteAndFlush(String id);
}
