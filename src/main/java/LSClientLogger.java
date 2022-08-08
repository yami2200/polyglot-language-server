import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

public class LSClientLogger {

    private static LSClientLogger INSTANCE; // Static instance of LSClientLogger
    private LanguageClient client; // Reference to the language client connected to Polyglot Language Server
    private boolean isInitialized;

    private LSClientLogger() {
    }

    /**
     * Initialize the LSClient Logger
     * @param languageClient language client connected to Polyglot Language Server
     */
    public void initialize(LanguageClient languageClient) {
        if (!Boolean.TRUE.equals(isInitialized)) {
            this.client = languageClient;
        }
        isInitialized = true;
    }

    /**
     * Return instance of LSClientLogger
     * @return instance of LSClientLogger
     */
    public static LSClientLogger getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LSClientLogger();
        }
        return INSTANCE;
    }

    /**
     * Log Message to client in the Polyglot Output Channel
     * @param message message to send to the client
     */
    public synchronized void logMessage(String message) {
        if (!isInitialized) {
            return;
        }
        client.logMessage(new MessageParams(MessageType.Info, message));
    }

}
