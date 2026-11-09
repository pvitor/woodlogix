package com.woodlogix.application.workpiece;

import com.woodlogix.application.transaction.TransactionPort;

import java.util.List;
import java.util.Objects;

public class ProjectWorkpiece {
    private final ProjectWorkpiecePort workpieces;
    private final TransactionPort transactions;

    public ProjectWorkpiece(ProjectWorkpiecePort workpieces, TransactionPort transactions) {
        this.workpieces = Objects.requireNonNull(workpieces);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public ProjectWorkpieceRow create(ProjectWorkpieceInput input) {
        return transactions.executeWithResult(() -> workpieces.createAndFlush(input));
    }

    public ProjectWorkpieceRow update(String id, ProjectWorkpieceInput input) {
        return transactions.executeWithResult(() -> workpieces.updateAndFlush(id, input)
                .orElseThrow(ProjectWorkpieceNotFoundException::new));
    }

    public void delete(String id) {
        transactions.execute(() -> {
            if (!workpieces.deleteAndFlush(id)) {
                throw new ProjectWorkpieceNotFoundException();
            }
        });
    }

    public List<ProjectWorkpieceRow> findByProject(String projectId) {
        return transactions.executeReadOnly(
                () -> workpieces.findByProjectOrderByName(projectId));
    }

    public List<ProjectWorkpieceRow> findAll() {
        return transactions.executeReadOnly(workpieces::findAllOrderByName);
    }
}
