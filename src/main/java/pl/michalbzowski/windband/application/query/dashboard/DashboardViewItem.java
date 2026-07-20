package pl.michalbzowski.windband.application.query.dashboard;

import lombok.Data;
import pl.michalbzowski.windband.domain.dashboard.SupersetDashboard;


/**
 * DTO for the dashboard list view (available dashboards for current user's band).
 */
@Data
public class DashboardViewItem {
    private Long id;
    private Integer supersetId;
    private String title;
    private String slug;
    private String description;
    private String icon;
    private String embedUrl;
    private String guestToken;

    public static DashboardViewItem fromEntity(SupersetDashboard dashboard) {
        DashboardViewItem item = new DashboardViewItem();
        item.setId(dashboard.getId());
        item.setSupersetId(dashboard.getSupersetId());
        item.setTitle(dashboard.getTitle());
        item.setSlug(dashboard.getSlug());
        item.setDescription(dashboard.getDescription());
        item.setIcon(dashboard.getIcon() != null ? dashboard.getIcon() : "fa-chart-bar");
        return item;
    }
}
