package pl.michalbzowski.windband.infrastructure.superset;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTOs for Superset API responses.
 */
public final class SupersetApiDtos {

    private SupersetApiDtos() {}

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String provider;
        private boolean refresh;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
            this.provider = "db";
            this.refresh = true;
        }
    }

    @Data
    public static class LoginResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        public String getToken() {
            return accessToken;
        }
    }

    @Data
    public static class DashboardListResponse {
        private List<DashboardEntry> result;
        private int count;
    }

    @Data
    public static class DashboardEntry {
        private Integer id;
        private String uuid;
        @JsonProperty("dashboard_title")
        private String dashboardTitle;
        private String slug;
        private String url;
        @JsonProperty("thumbnail_url")
        private String thumbnailUrl;
        private String published;
        private Map<String, Object> metadata;
    }

    @Data
    public static class DashboardDetailResponse {
        private DashboardDetail result;
    }

    @Data
    public static class DashboardDetail {
        private Integer id;
        @JsonProperty("dashboard_title")
        private String dashboardTitle;
        private String slug;
        private String published;
    }

    @Data
    public static class GuestTokenRequest {
        private User user;
        private List<Resource> resources;
        private List<RlsRule> rls;

        @Data
        public static class User {
            private String username;
        }

        @Data
        public static class Resource {
            private String type;
            private String id;
        }

        @Data
        public static class RlsRule {
            private String datasetId;
            private String clause;
        }
    }

    @Data
    public static class GuestTokenResponse {
        private String token;
    }

    @Data
    public static class CsrfTokenResponse {
        private String result;
    }

    @Data
    public static class ApiTokenResponse {
        private String token;
    }
}
