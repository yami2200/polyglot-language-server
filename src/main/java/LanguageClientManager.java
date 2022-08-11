import com.example.polyglotast.PolyglotTreeHandler;
import org.eclipse.lsp4j.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class LanguageClientManager {
    private final LSClientLogger clientLogger; // reference to the polyglot client logger
    HashMap<String, LanguageServerClient> languageClients; // Map of programming language name linked to their language client
    HashMap<LanguageServerClient, Process> languageServersProcess; // Map of language clients linked to their language server process
    PolyglotLanguageServer languageServer; // Polyglot language server reference
    CompletableFuture<Object> shutdownFuture; // Future of shutdown request

    public LanguageClientManager(PolyglotLanguageServer languageServer){
        this.languageClients = new HashMap<>();
        this.languageServersProcess = new HashMap<>();
        this.languageServer = languageServer;
        this.clientLogger = LSClientLogger.getInstance();
    }

    /**
     * Send LSP didOpen notification to the proper language server (depending on file extension in params)
     * @param params DidOpenTextDocumentParams
     */
    public void didOpenRequest(DidOpenTextDocumentParams params){
        System.out.println(params);
        String language = params.getTextDocument().getLanguageId();
        if(languageClients.containsKey(language)){
            languageClients.get(language).didOpenRequest(params);
            return;
        }
        LanguageServerClient newclient = createNewClient(language);
        if(newclient == null) return;
        newclient.didOpenRequest(params);
    }

    /**
     * Send LSP didChange notification to the proper language server (depending on file extension in params)
     * @param params DidOpenTextDocumentParams
     */
    public void didChangeRequest(DidChangeTextDocumentParams params){
        String language = "";
        try {
            language = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(params.getTextDocument().getUri()))).getLang();
        } catch (URISyntaxException e) {
            System.err.println(e);
            return;
        }
        if(languageClients.containsKey(language)){
            languageClients.get(language).didChangeRequest(params);
            return;
        }
        LanguageServerClient newclient = createNewClient(language);
        if(newclient == null) return;
        newclient.didChangeRequest(params);
    }

    /**
     * Send LSP didSave notification to the proper language server (depending on file extension in params)
     * @param params DidSaveTextDocumentParams
     */
    public void didSaveRequest(DidSaveTextDocumentParams params){
        String language = "";
        try {
            language = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(params.getTextDocument().getUri()))).getLang();
        } catch (URISyntaxException e) {
            System.err.println(e);
            return;
        }
        if(languageClients.containsKey(language)){
            languageClients.get(language).didSaveRequest(params);
            return;
        }
        LanguageServerClient newclient = createNewClient(language);
        if(newclient == null) return;
        newclient.didSaveRequest(params);
    }

    /**
     * Send LSP didRename notification to the proper language server (depending on file extension in params)
     * @param params RenameFilesParams
     */
    public void didRenameFiles(RenameFilesParams params){
        for (FileRename file : params.getFiles()) {
            String language = "";
            try {
                language = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(file.getOldUri()))).getLang();
                RenameFilesParams param = new RenameFilesParams();
                param.setFiles(Arrays.asList(file));
                if(languageClients.containsKey(language)){
                    languageClients.get(language).didRenameFiles(param);
                } else {
                    LanguageServerClient newclient = createNewClient(language);
                    if(newclient != null) newclient.didRenameFiles(param);
                }
            } catch (URISyntaxException e) {
                System.err.println(e);
            }
        }
    }

    /**
     * Send LSP hover request to the proper language server (depending on file extension in params)
     * @param params HoverParams
     * @return LSP response future from language server
     */
    public CompletableFuture<Hover> hoverRequest(HoverParams params){
        CompletableFuture<Hover> future = new CompletableFuture<>();
        String language = "";
        try {
            language = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(params.getTextDocument().getUri()))).getLang();
        } catch (URISyntaxException e) {
            System.err.println(e);
            return null;
        }
        if(languageClients.containsKey(language)){
            languageClients.get(language).hoverRequest(params).thenApply((v) -> {
                try{
                    Hover hov = (Hover) v;
                    future.complete(hov);
                } catch (Exception e){
                    future.complete(null);
                }
               return v;
            });
            return future;
        }
        LanguageServerClient newclient = createNewClient(language);
        if(newclient == null) return null;
        newclient.hoverRequest(params).thenApply((v) -> {
            try{
                Hover hov = (Hover) v;
                future.complete(hov);
            } catch (Exception e){
                future.complete(null);
            }
            return v;
        });
        return future;
    }

    /**
     *  Send LSP shutdown request to all language servers
     * @return future response when all language servers are shutdown
     */
    public CompletableFuture<Object> shutdown(){
        this.shutdownFuture = new CompletableFuture<Object>();
        for (LanguageServerClient client : this.languageClients.values()) {
            try{
                client.shutdown().thenApply((v) -> {
                    Process p = this.languageServersProcess.get(client);
                    if(p != null && p.isAlive()) p.destroy();
                    this.languageServersProcess.remove(client);
                    client.interrupt();
                    if(languageServersProcess.size() == 0){
                        this.shutdownFuture.complete(new Object());
                    }
                    return v;
                });
            } catch (Exception e){
                System.err.println(e);
            }
        }
        return this.shutdownFuture;
    }

    /**
     * Create a new language client, by launching a new language server and connects to it
     * @param language programming language of the language server to connects to
     * @return A language server client if the client is connected to the language server, null otherwise
     */
    public LanguageServerClient createNewClient(String language){
        PolyglotLanguageServerProperties.LanguageServerInfo lsInfo = this.languageServer.getLanguageInfo(language);
        if(lsInfo == null){
            this.clientLogger.logMessage("No language Server configured for the language : "+language);
            System.err.println("No language Server configured for the language : "+language);
            return null;
        }
        LanguageServerClient client = new LanguageServerClient(lsInfo.language, lsInfo.ip, lsInfo.port, this.languageServer);
        if(this.initializeConnection(lsInfo, client)){
            this.languageClients.put(language, client);
            client.start();
            if(client.connect()){
                this.clientLogger.logMessage("Successfully connected to language Server configured for the language : "+language);
                return client;
            } else {
                this.clientLogger.logMessage("Couldn't connect to language Server configured for the language : "+language);
            }
        }
        return null;
    }

    /**
     * Launch a language server by creating a new process
     * @param lsInfo Language Server Information properties
     * @param client Language client of the server
     * @return the language server has been created successfully
     */
    public boolean initializeConnection(PolyglotLanguageServerProperties.LanguageServerInfo lsInfo, LanguageServerClient client){
        if(this.languageClients.containsKey(lsInfo.language)){
            if(this.languageClients.get(lsInfo.language) != null){
                this.languageClients.get(lsInfo.language).shutdown();
                this.languageClients.get(lsInfo.language).interrupt();
            }
            this.languageClients.remove(lsInfo.language);
        }
        if(this.languageServersProcess.containsKey(lsInfo.language)){
            this.languageServersProcess.get(lsInfo.language).destroy();
            this.languageServersProcess.remove(lsInfo.language);
        }

        try {
            File file = new File(PolyglotLanguageServer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
            ProcessBuilder builder = new ProcessBuilder(lsInfo.command).directory(new File(file.getParent()));
            Process process = builder.inheritIO().start();
            this.languageServersProcess.put(client, process);
            return true;
        } catch (IOException e) {
            System.err.println(e);
            this.clientLogger.logMessage(e.getMessage());
            return false;
        } catch (URISyntaxException e) {
            System.err.println(e);
            this.clientLogger.logMessage(e.getMessage());
            return false;
        }
    }



}
