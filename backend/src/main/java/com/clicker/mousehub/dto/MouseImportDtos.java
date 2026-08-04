package com.clicker.mousehub.dto;

import java.util.List;

public final class MouseImportDtos {
    private MouseImportDtos() {}

    public record ImportIssue(long row, String field, String value, String message) {}
    public record ImportPreview(String checksum, String filename, int totalRows, int validRows,
                                int createRows, int updateRows, List<ImportIssue> errors, boolean ready) {}
    public record ImportResult(String checksum, int createdCount, int updatedCount, boolean alreadyImported) {}
}
