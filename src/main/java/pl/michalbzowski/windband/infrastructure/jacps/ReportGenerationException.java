package pl.michalbzowski.windband.infrastructure.jacps;

/** Exception thrown when report generation fails */
public class ReportGenerationException extends RuntimeException {
    
    public ReportGenerationException(String message) {
        super(message);
    }
    
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
