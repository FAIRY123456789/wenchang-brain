package cn.wenchang.brain.search;

public class SearchProviderException extends RuntimeException {

    private final String errorType;

    public SearchProviderException(String errorType, String message) {
        super(message);
        this.errorType = errorType == null || errorType.isBlank() ? "SEARCH_ERROR" : errorType;
    }

    public SearchProviderException(String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType == null || errorType.isBlank() ? "SEARCH_ERROR" : errorType;
    }

    public String errorType() { return errorType; }
}
