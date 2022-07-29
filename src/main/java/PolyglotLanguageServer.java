import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PolyglotLanguageServer implements LanguageServer, LanguageClientAware {

    private TextDocumentService textDocumentService;
    private WorkspaceService workspaceService;
    private ClientCapabilities clientCapabilities;
    LanguageClient languageClient;
    private int shutdown = 1;
    protected InitializeParams initializationParams;
    protected LanguageClientManager languageClientManager;
    protected PolyglotLanguageServerProperties properties;
    private CompletableFuture shutdownFuture;

    public PolyglotLanguageServer() {
        this.textDocumentService = new PolyglotTextDocumentService(this);
        this.workspaceService = new PolyglotWorkspaceService(this);
    }

    @Override
    public void connect(LanguageClient languageClient) {
        this.languageClient = languageClient;
        LSClientLogger.getInstance().initialize(this.languageClient);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        final InitializeResult response = new InitializeResult(new ServerCapabilities());
        //Set the document synchronization capabilities to full.
        response.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Incremental);
        this.initializationParams = initializeParams;
        //response.getCapabilities().setHoverProvider(true);
        this.clientCapabilities = initializeParams.getCapabilities();

        this.properties = getProperties();
        if(this.properties == null){
            throw new RuntimeException("Properties of Polyglot Language Server has not been loaded correctly");
        }

        this.languageClientManager = new LanguageClientManager(this);

        /* Check if dynamic registration of completion capability is allowed by the client. If so we don't register the capability.
           Else, we register the completion capability.
         */
        if (!isDynamicCompletionRegistration()) {
            response.getCapabilities().setCompletionProvider(new CompletionOptions());
        }
        return CompletableFuture.supplyAsync(() -> response);
    }

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

    public PolyglotLanguageServerProperties.LanguageServerInfo getLanguageInfo(String language){
        System.err.println(this.properties.ls);
        for (PolyglotLanguageServerProperties.LanguageServerInfo l : this.properties.ls) {
            if(l.language.equals(language)) return l;
        }
        return null;
    }

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

    private boolean isDynamicCompletionRegistration() {
        TextDocumentClientCapabilities textDocumentCapabilities = clientCapabilities.getTextDocument();
        return textDocumentCapabilities != null && textDocumentCapabilities.getCompletion() != null && Boolean.FALSE.equals(textDocumentCapabilities.getCompletion().getDynamicRegistration());
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdown = 0;
        return CompletableFuture.supplyAsync(Object::new);
    }

    @Override
    public void exit() {
        this.languageClientManager.shutdown();
        System.exit(shutdown);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return this.textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return this.workspaceService;
    }
}
