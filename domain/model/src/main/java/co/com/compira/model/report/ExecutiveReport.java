package co.com.compira.model.report;

import co.com.compira.model.task.TaskStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExecutiveReport(UUID reportId, String version, OffsetDateTime generatedAt, String timeZone,
        String scope, String generatedBy, Filter filters, Metrics summary, Quality quality,
        List<Group> teams, List<Group> assignees, List<Distribution> distribution,
        List<Month> monthlyCohorts, List<String> findings, List<String> recommendations, List<Item> tasks) {
    public enum DateBasis { CREATED, DUE, CLOSED }
    public record Filter(LocalDate from, LocalDate to, DateBasis dateBasis, UUID teamId,
                         UUID responsibleId, boolean unassigned, TaskStatus status) {
        public Filter {
            if (dateBasis == null) dateBasis = DateBasis.CREATED;
            if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("La fecha inicial no puede superar la final");
            if (unassigned && responsibleId != null) throw new IllegalArgumentException("No combine responsable con sin responsable");
            if ((from != null && (from.getYear() < 1900 || from.getYear() > 9998)) ||
                (to != null && (to.getYear() < 1900 || to.getYear() > 9998))) throw new IllegalArgumentException("Fecha fuera del rango permitido");
        }
    }
    public record Metrics(long total, long active, long pending, long inProgress, long completed, long closed,
            long cancelled, long overdue, long dueSoon, long unassigned, long evaluableClosed, long closedOnTime,
            Integer progressPercentage, Integer compliancePercentage, Double averageClosureHours,
            Double medianClosureHours, Double averageActiveAgeHours, Double oldestActiveAgeHours, Double maximumOverdueHours) { }
    public record Quality(long missingDueDate, long missingTeam, long closedWithoutHistory, long invalidClosureDates) { }
    public record Group(UUID id, String name, String email, Metrics metrics) { }
    public record Distribution(TaskStatus status, long count) { }
    public record Month(String month, long created, long closed) { }
    public record Item(UUID id, String title, UUID teamId, String teamName, UUID responsibleId,
            String responsibleName, String responsibleEmail, TaskStatus status, OffsetDateTime createdAt,
            OffsetDateTime dueDate, OffsetDateTime closedAt, OffsetDateTime lastActivityAt, long observations,
            Double overdueHours, Double ageHours, Double closureHours, Boolean closedOnTime) { }
}
