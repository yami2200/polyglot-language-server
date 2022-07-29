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

    public LanguageServerClient(String language, String ip, int port, PolyglotLanguageServer polyglotLSref){
        this.language = language;
        this.ip = ip;
        this.port = port;
        this.pendingInitializationRequests = new ArrayList<>();
        this.polyglotLSref = polyglotLSref;
    }

    public boolean connect(){
        int connectionTry = 3;
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
                    Thread.sleep(400);
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

    public void shutdown(){
        try {
            this.input.close();
            this.output.close();
            this.clientSocket.close();
        } catch (IOException e) {}
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
            CompletableFuture<Object> result = null;
             switch (pendingInitializationRequest.id) {
                 case "didOpenRequest":
                     result = didOpenRequest((DidOpenTextDocumentParams) pendingInitializationRequest.params);
                 case "hoverRequest":
                     result = hoverRequest((HoverParams) pendingInitializationRequest.params);
            };
            if(result != null){
                result.thenApply((v) -> {
                    pendingInitializationRequest.response.complete(v);
                    return v;
                });
            }
        }
    }

    public synchronized CompletableFuture<Object> didOpenRequest(DidOpenTextDocumentParams params){
        System.err.println("Request didOpen");
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "didOpenRequest");
        if(future == null) return this.remoteEndpoint.request("textDocument/didOpen", params);
        return future;
    }

    public synchronized CompletableFuture<Object> hoverRequest(HoverParams params){
        CompletableFuture<Object> future = this.checkRequestPreInitialization(params, "hoverRequest");
        if(future == null) return this.remoteEndpoint.request("textDocument/hover", params);
        return future;
    }

    private CompletableFuture<Object> checkRequestPreInitialization(Object params, String requestId){
        if(!this.isInitialized){
            CompletableFuture<Object> future = new CompletableFuture<>();
            this.pendingInitializationRequests.add(new LSRequest(requestId, params, future));
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
