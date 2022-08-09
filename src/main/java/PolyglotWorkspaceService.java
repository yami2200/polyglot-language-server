import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class PolyglotWorkspaceService implements WorkspaceService {

    private PolyglotLanguageServer languageServer; // Reference to the Polyglot Language Server
    LSClientLogger clientLogger; // Reference to LSClient Logger to print message to the client

    public PolyglotWorkspaceService(PolyglotLanguageServer languageServer) {
        this.languageServer = languageServer;
        this.clientLogger = LSClientLogger.getInstance();
    }

    /**
     * LSP didRename Notification Handler
     * @param params RenameFilesParams
     */
    @Override
    public void didRenameFiles(RenameFilesParams params) {
        this.clientLogger.logMessage("Operation 'workspace/didRenameFiles' Ack");
        this.languageServer.languageClientManager.didRenameFiles(params);
    }

    /**
     * LSP didChangeConfiguration Notification Handler
     * @param didChangeConfigurationParams DidChangeConfigurationParams
     */
    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams didChangeConfigurationParams) {
        this.clientLogger.logMessage("Operation 'workspace/didChangeConfiguration' Ack");
    }

    /**
     * LSP didChangeWatchedFiles Notification Handler
     * @param didChangeWatchedFilesParams DidChangeWatchedFilesParams
     */
    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams didChangeWatchedFilesParams) {
        this.clientLogger.logMessage("Operation 'workspace/didChangeWatchedFiles' Ack");
    }
}
