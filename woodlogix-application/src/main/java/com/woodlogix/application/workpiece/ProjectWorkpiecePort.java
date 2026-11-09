package com.woodlogix.application.workpiece;

import java.util.List;
import java.util.Optional;

public interface ProjectWorkpiecePort {
    ProjectWorkpieceRow createAndFlush(ProjectWorkpieceInput input);

    Optional<ProjectWorkpieceRow> updateAndFlush(String id, ProjectWorkpieceInput input);

    boolean deleteAndFlush(String id);

    List<ProjectWorkpieceRow> findByProjectOrderByName(String projectId);

    List<ProjectWorkpieceRow> findAllOrderByName();
}
