package pl.michalbzowski.windband.application.query.dashboard;

import lombok.Data;

import java.util.List;

/**
 * Result of syncing dashboards from Superset.
 */
@Data
public class DashboardSyncResult {
    private int totalInSuperset;
    private int added;
    private int updated;
    private int unchanged;
    private List<String> errors;

    public DashboardSyncResult() {
        this.errors = new java.util.ArrayList<>();
    }

    public void incrementAdded() { this.added++; }
    public void incrementUpdated() { this.updated++; }
    public void incrementUnchanged() { this.unchanged++; }
    public void addError(String error) { this.errors.add(error); }
}
