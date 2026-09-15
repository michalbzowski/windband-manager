package pl.michalbzowski.windband.domain.composition;

public enum CompositionStatus {
    DRAFT("Szkic", "info"),
    READY("Gotowy", "success"),
    ARCHIVED("Zarchiwizowany", "secondary");

    private final String displayName;
    private final String cssClass;

    CompositionStatus(String displayName, String cssClass) {
        this.displayName = displayName;
        this.cssClass = cssClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCssClass() {
        return cssClass;
    }
}
