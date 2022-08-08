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

    private final String language;
    private final String ip;
    private final int port;
    private BufferedReader input;
    private PrintWriter output;
    private Socket clientSocket;
    private boolean isInitialized = false;
    private RemoteEndpoint remoteEndpoint;
    private PolyglotLanguageServer polyglotLSref;
    private ArrayList<LSRequest> pendingInitializationRequests;
    private LSClientLogger clientLogger;
    CompletableFuture<Object> shutdownFuture;

    public LanguageServerClient(String language, String ip, int port, PolyglotLanguageServer polyglotLSref){
        this.language = language;
        this.ip = ip;
        this.port = port;
        this.pendingInitializationRequests = new ArrayList<>();
        this.polyglotLSref = polyglotLSref;
        this.clientLogger = LSClientLogger.getInstance();
    }

    public boolean connect(){
        int connectionTry = 10;
        while(connectionTry > 0){
            connectionTry--;
            try {
                this.clientSocket = new Socket(this.ip, this.port);
                this.input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                this.output = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
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

    public boolean isConnected(){
        return this.clientSocket != null && this.clientSocket.isConnected();
    }

    public boolean isInitialized(){
        return this.isInitialized;
    }

    public CompletableFuture<Object> shutdown(){
        this.shutdownFuture = new CompletableFuture<>();
        this.remoteEndpoint.request("shutdown", null).thenApply((v) -> {
            this.remoteEndpoint.notify("exit", null);
            this.shutdownFuture.complete(new Object());
            return v;
        });
        return this.shutdownFuture;
    }

    private void initializeConnection(LanguageServer remoteProxy) {
        InitializeParams params = this.polyglotLSref.initializationParams;
        remoteProxy.initialize(params).thenApply(k -> {
            remoteProxy.initialized(new InitializedParams());
            this.initialized();
            return k;
        });
    }

    private void initialized(){
        this.isInitialized = true;
        for (LSRequest pendingInitializationRequest : this.pendingInitializationRequests) {
            Object result_f = pendingInitializationRequest.function.apply(pendingInitializationRequest.params);
            if(result_f == null) return;
            CompletableFuture<Object> result = (CompletableFuture<Object>) result_f;
            if(result != null){
                result.thenApply((v) -> {
                    pendingInitializationRequest.response.complete(v);
                    return v;
                });
            }
        }
    }

    public void didOpenRequest(DidOpenTextDocumentParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "didOpenRequest", (param) -> {this.didOpenRequest((DidOpenTextDocumentParams) param);return null;});
        if(future == null) {
            this.clientLogger.logMessage("didOpenRequest to "+this.language+" language server at URI : "+params.getTextDocument().getUri());
            this.remoteEndpoint.notify("textDocument/didOpen", params);
        }
    }

    public void didChangeRequest(DidChangeTextDocumentParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "didChangeRequest", (param) -> {this.didChangeRequest((DidChangeTextDocumentParams) param);return null;});
        if(future == null){
            this.clientLogger.logMessage("change request to LS "+this.language);
            this.remoteEndpoint.notify("textDocument/didChange", params);
        }
    }

    public void didSaveRequest(DidSaveTextDocumentParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "didSaveRequest", (param) -> {this.didSaveRequest((DidSaveTextDocumentParams) param);return null;});
        if(future == null) this.remoteEndpoint.notify("textDocument/didSave", params);
    }

    public void didRenameFiles(RenameFilesParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "didRenameFiles", (param) -> {this.didRenameFiles((RenameFilesParams) param);return null;});
        if(future == null) this.remoteEndpoint.notify("workspace/didRenameFiles", params);
    }

    public CompletableFuture<Object> hoverRequest(HoverParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "hoverRequest", (param) -> {return this.hoverRequest((HoverParams) param);});
        if(future == null) {
            this.clientLogger.logMessage("Request from LS "+this.language);
            return this.remoteEndpoint.request("textDocument/hover", params);
        }
        return future;
    }

    private CompletableFuture<Object> checkRequestPreInitialization(Object params, String requestId, Function function){
        if(!this.isInitialized){
            CompletableFuture<Object> future = new CompletableFuture<>();
            this.pendingInitializationRequests.add(new LSRequest(requestId, params, future, function));
            return future;
        }
        return null;
    }

    /**
     * LANGUAGE CLIENT METHOD SECTION
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
