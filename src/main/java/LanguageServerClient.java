import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class LanguageServerClient extends Thread implements LanguageClient {

    private final String language; // Programming language of language server which the client connects to
    private final String ip; // IP of language server which the client connects to
    private final int port; // port of language server which the client connects to
    private Socket clientSocket; // Socket to handle connection with the language server
    private boolean isInitialized = false; // Store if the client is initialized (exchange initialization request with server)
    private RemoteEndpoint remoteEndpoint; // Endpoint used to make request & notification to the server
    private PolyglotLanguageServer polyglotLSref; // Reference to the polyglot language server
    private ArrayList<LSRequest> pendingInitializationRequests; // List of requests waiting the initialization, before to be sent
    private LSClientLogger clientLogger; // Reference to the Polyglot client logger
    CompletableFuture<Object> shutdownFuture; // CompletableFuture used to store Shutdown Future

    public LanguageServerClient(String language, String ip, int port, PolyglotLanguageServer polyglotLSref){
        this.language = language;
        this.ip = ip;
        this.port = port;
        this.pendingInitializationRequests = new ArrayList<>();
        this.polyglotLSref = polyglotLSref;
        this.clientLogger = LSClientLogger.getInstance();
    }

    /**
     * Connects to the language server
     * @return the client is connected to the language server
     */
    public boolean connect(){
        int connectionTry = 10;
        while(connectionTry > 0){
            connectionTry--;
            try {
                this.clientSocket = new Socket(this.ip, this.port);
                Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(this, clientSocket.getInputStream(), clientSocket.getOutputStream());
                this.initializeConnection(launcher.getRemoteProxy());
                this.remoteEndpoint = launcher.getRemoteEndpoint();
                launcher.startListening();
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Verify if the client is connected to the language server
     * @return the client is connected to the language server
     */
    public boolean isConnected(){
        return this.clientSocket != null && this.clientSocket.isConnected();
    }

    /**
     * Verify if the client has been initialized with LSP
     * @return the client has been initialized with LSP
     */
    public boolean isInitialized(){
        return this.isInitialized;
    }

    /**
     * Send shutdown request & exit notification to the language server
     * @return response from language server
     */
    public CompletableFuture<Object> shutdown(){
        this.shutdownFuture = new CompletableFuture<>();
        this.remoteEndpoint.request("shutdown", null).thenApply((v) -> {
            this.remoteEndpoint.notify("exit", null);
            this.shutdownFuture.complete(new Object());
            return v;
        });
        return this.shutdownFuture;
    }

    /**
     * Send Initialize request & initialized notification to the language server
     * @param remoteProxy LSP4J Language Server ref
     */
    private void initializeConnection(LanguageServer remoteProxy) {
        InitializeParams params = this.polyglotLSref.initializationParams;
        // Params can be use to constrain rename workspace edit types
        //params.getCapabilities().getWorkspace().getWorkspaceEdit().setDocumentChanges(false);
        //params.getCapabilities().getWorkspace().getWorkspaceEdit().setResourceOperations(new ArrayList<>());
        remoteProxy.initialize(params).thenApply(k -> {
            remoteProxy.initialized(new InitializedParams());
            this.initialized();
            return k;
        });
    }

    /**
     * Final step to initialize the client, send all requests which were waiting the initialization
     */
    private void initialized(){
        this.isInitialized = true;
        for (LSRequest pendingInitializationRequest : this.pendingInitializationRequests) {
            try {
                Object result_f = pendingInitializationRequest.function.apply(pendingInitializationRequest.params);
                if(result_f != null){
                    CompletableFuture<Object> result = (CompletableFuture<Object>) result_f;
                    if(result != null){
                        result.thenApply((v) -> {
                            pendingInitializationRequest.response.complete(v);
                            return v;
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println(e);
            }
        }
    }

    /**
     * Send didOpen LSP notification to the language Server
     * @param params DidOpenTextDocumentParams
     */
    public synchronized void didOpenRequest(DidOpenTextDocumentParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, (param) -> {this.didOpenRequest((DidOpenTextDocumentParams) param);return null;});
        if(future == null) {
            this.clientLogger.logMessage("didOpenRequest to "+this.language+" language server at URI : "+params.getTextDocument().getUri());
            this.remoteEndpoint.notify("textDocument/didOpen", params);
        }
    }

    /**
     * Send didChangeRequest LSP notification to the language Server
     * @param params DidChangeTextDocumentParams
     */
    public synchronized void didChangeRequest(DidChangeTextDocumentParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, (param) -> {this.didChangeRequest((DidChangeTextDocumentParams) param);return null;});
        if(future == null){
            this.clientLogger.logMessage("change request to LS "+this.language);
            this.remoteEndpoint.notify("textDocument/didChange", params);
        }
    }

    /**
     * Send didSaveRequest LSP notification to the language Server
     * @param params DidSaveTextDocumentParams
     */
    public synchronized void didSaveRequest(DidSaveTextDocumentParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, (param) -> {this.didSaveRequest((DidSaveTextDocumentParams) param);return null;});
        if(future == null) this.remoteEndpoint.notify("textDocument/didSave", params);
    }

    /**
     * Send didRenameFiles LSP notification to the language Server
     * @param params RenameFilesParams
     */
    public synchronized void didRenameFiles(RenameFilesParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, (param) -> {this.didRenameFiles((RenameFilesParams) param);return null;});
        if(future == null) this.remoteEndpoint.notify("workspace/didRenameFiles", params);
    }

    /**
     * Send hover LSP Request to the language Server
     * @param params HoverParams
     */
    public synchronized CompletableFuture<Object> hoverRequest(HoverParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, (param) -> {return this.hoverRequest((HoverParams) param);});
        if(future == null) {
            this.clientLogger.logMessage("Request from LS "+this.language);
            return this.remoteEndpoint.request("textDocument/hover", params);
        }
        return future;
    }

    /**
     * Send rename LSP Request to the language Server
     * @param params RenameParams
     */
    public synchronized CompletableFuture<Object> renameRequest(RenameParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, (param) -> {return this.renameRequest((RenameParams) param);});
        if(future == null) {
            this.clientLogger.logMessage("Request to LS "+this.language);
            return this.remoteEndpoint.request("textDocument/rename", params);
        }
        return future;
    }

    /**
     * Return null if the client is initialized, otherwise add the request to pending initialization list and return the future of the request
     * @param params Parameters of the request
     * @param function function to call when the client will be initialized
     * @return null if the client is initialized, the future of the pending request
     */
    private synchronized CompletableFuture<Object> checkRequestPreInitialization(Object params, Function function){
        if(!this.isInitialized){
            CompletableFuture<Object> future = new CompletableFuture<>();
            this.pendingInitializationRequests.add(new LSRequest(params, future, function));
            return future;
        }
        return null;
    }

    /**
     * LANGUAGE CLIENT METHOD SECTION, METHODS NOT USED
     */
    @Override
    public void telemetryEvent(Object object) {

    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {

    }

    @Override
    public void showMessage(MessageParams messageParams) {

    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return null;
    }

    @Override
    public void logMessage(MessageParams message) {

    }
}
