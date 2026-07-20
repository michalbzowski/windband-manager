package pl.michalbzowski.windband.application.query.dashboard;

import lombok.Data;

import java.util.List;

/**
 * DTO for the admin dashboard management view.
 */
@Data
public class AdminDashboardView {
    private Long id;
    private Integer supersetId;
    private String title;
    private String slug;
    private String icon;
    private boolean active;
    private int position;
    private List<BandAssignment> bandAssignments;

    @Data
    public static class BandAssignment {
        private Long bandId;
        private String bandName;
        private boolean assigned;
        private boolean autoAssignNew;
    }
}
