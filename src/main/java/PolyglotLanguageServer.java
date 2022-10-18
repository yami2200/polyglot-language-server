import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PolyglotLanguageServer implements LanguageServer, LanguageClientAware {

    private TextDocumentService textDocumentService; // Text Document Service ref -> handle LSP textDocument requests
    private WorkspaceService workspaceService; // Workspace Service ref -> handle LSP Workspace requests
    private ClientCapabilities clientCapabilities; // Clients capabilities sent by the client during initialization
    LanguageClient languageClient; // Reference to the language Client
    private int shutdown = 1; // Shutdown Code
    protected InitializeParams initializationParams; // Initialize Parameters used for all Language Servers initializations
    protected LanguageClientManager languageClientManager; // Language Clients Manager for all Language Servers of specific language
    protected PolyglotLanguageServerProperties properties; // Properties of the Polyglot Language Server
    CompletableFuture<Object> shutdownFuture; // Completable Future for the shutdown of the server

    public PolyglotLanguageServer() {
        this.textDocumentService = new PolyglotTextDocumentService(this);
        this.workspaceService = new PolyglotWorkspaceService(this);

        Thread closeChildLSThread = new Thread() {
            public void run() {
                if(languageClientManager == null || languageClientManager.languageServersProcess == null) return;
                for (Process p : languageClientManager.languageServersProcess.values()) {
                    if(p!=null) p.destroy();

                };
            }
        };

        Runtime.getRuntime().addShutdownHook(closeChildLSThread);
    }

    /**
     * Event fired when a client connects to the server
     * @param languageClient language client which is connected to the server
     */
    @Override
    public void connect(LanguageClient languageClient) {
        this.languageClient = languageClient;
        // Initialize Client Logger
        LSClientLogger.getInstance().initialize(this.languageClient);
    }

    /**
     * LSP initialize request Handler
     * @param initializeParams Initialize Parameters
     * @return Initialize Result (Server capabilities)
     */
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        final InitializeResult response = new InitializeResult(new ServerCapabilities());

        //Set the document synchronization capabilities to full (each change request will send all the code of the file, not only the edition)
        response.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);

        this.initializationParams = initializeParams;

        this.clientCapabilities = initializeParams.getCapabilities();

        // Set Hover Provider to true
        response.getCapabilities().setHoverProvider(true);

        // Set Rename Provider to true
        response.getCapabilities().setRenameProvider(true);

        // Set Workspace capabilities (tests for enabling workspace operations capabilities)
        /*FileOperationOptions fileOperationOptions = new FileOperationOptions();
        FileOperationFilter fileOperationFilter = new FileOperationFilter();
        fileOperationFilter.setScheme("file");
        FileOperationPattern fileOperationPattern = new FileOperationPattern();*/
        //fileOperationPattern.setGlob("**/*.{py,js}");
        /*fileOperationPattern.setMatches(FileOperationPatternKind.File);
        fileOperationFilter.setPattern(fileOperationPattern);
        fileOperationOptions.setFilters(Arrays.asList(fileOperationFilter));*/

        //response.getCapabilities().getWorkspace().getFileOperations().setDidCreate(fileOperationOptions);
        //response.getCapabilities().getWorkspace().getFileOperations().setDidDelete(fileOperationOptions);

        WorkspaceFoldersOptions workspaceFoldersOptions = new WorkspaceFoldersOptions();
        workspaceFoldersOptions.setSupported(true);
        workspaceFoldersOptions.setChangeNotifications(true);
        //response.getCapabilities().getWorkspace().setWorkspaceFolders(workspaceFoldersOptions);

        // Get server properties
        this.properties = getProperties();
        if(this.properties == null){
            throw new RuntimeException("Properties of Polyglot Language Server has not been loaded correctly");
        }

        // Create a Language Client Manager
        this.languageClientManager = new LanguageClientManager(this);

        // Register completion capability if the client can handle it
        if (!isDynamicCompletionRegistration()) {
            response.getCapabilities().setCompletionProvider(new CompletionOptions());
        }
        return CompletableFuture.supplyAsync(() -> response);
    }

    /**
     * Get Polyglot Language Server properties from the propertise.json in resources folder
     * @return the Language Server properties, or null if error occured
     */
    private PolyglotLanguageServerProperties getProperties(){
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream is = classLoader.getResourceAsStream("properties/properties.json");
            InputStreamReader streamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(streamReader);

            PolyglotLanguageServerProperties prop = new Gson().fromJson(reader, PolyglotLanguageServerProperties.class);
            return prop;
        } catch (JsonSyntaxException | JsonIOException e) {
            System.err.println(e);
        }
        return null;
    }

    /**
     * Get language Server info for specific language
     * @param language language of language server
     * @return information about language server of specific language, null if language is not found
     */
    public PolyglotLanguageServerProperties.LanguageServerInfo getLanguageInfo(String language){
        for (PolyglotLanguageServerProperties.LanguageServerInfo l : this.properties.ls) {
            if(l.language.equals(language)) return l;
        }
        return null;
    }

    /**
     * LSP Initialized Notification Handler
     * @param params Initialized Params
     */
    @Override
    public void initialized(InitializedParams params) {
        //Check if dynamic completion support is allowed, if so register.
        if (isDynamicCompletionRegistration()) {
            CompletionRegistrationOptions completionRegistrationOptions = new CompletionRegistrationOptions();
            Registration completionRegistration = new Registration(UUID.randomUUID().toString(),
                    "textDocument/completion", completionRegistrationOptions);
            languageClient.registerCapability(new RegistrationParams(List.of(completionRegistration)));
        }
    }

    /**
     * Return the dynamicCompletion capability
     * @return the dynamic completion is registered
     */
    private boolean isDynamicCompletionRegistration() {
        TextDocumentClientCapabilities textDocumentCapabilities = clientCapabilities.getTextDocument();
        return textDocumentCapabilities != null && textDocumentCapabilities.getCompletion() != null && Boolean.FALSE.equals(textDocumentCapabilities.getCompletion().getDynamicRegistration());
    }

    /**
     * LSP Shutdown Request Handler
     * @return future object (return value doesn't matter for this request except for error code)
     */
    @Override
    public CompletableFuture<Object> shutdown() {
        this.shutdownFuture = new CompletableFuture<Object>();
        this.languageClientManager.shutdown().thenApply(v -> {
            this.shutdownFuture.complete(new Object());
            return v;
        });
        shutdown = 0;
        return this.shutdownFuture;
    }

    /**
     * LSP Exit Notification Handler
     */
    @Override
    public void exit() {
        this.languageClientManager.shutdown();
        System.exit(shutdown);
    }

    /**
     * Get Polyglot Language Server Text Document Service Reference
     * @return Polyglot Language Server Text Document Service Reference
     */
    @Override
    public TextDocumentService getTextDocumentService() {
        return this.textDocumentService;
    }

    /**
     * Get Polyglot Language Server Workspace Service Reference
     * @return Polyglot Language Server Workspace Service Reference
     */
    @Override
    public WorkspaceService getWorkspaceService() {
        return this.workspaceService;
    }
}
