import org.eclipse.lsp4j.DidOpenTextDocumentParams;

import java.io.*;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class LanguageClientManager {
    HashMap<String, LanguageServerClient> languageClients;
    HashMap<String, Process> languageServersProcess;
    PolyglotLanguageServer languageServer;


    public LanguageClientManager(PolyglotLanguageServer languageServer){
        this.languageClients = new HashMap<>();
        this.languageServersProcess = new HashMap<>();
        this.languageServer = languageServer;
    }

    public CompletableFuture<Object> didOpenRequest(DidOpenTextDocumentParams params){
        String language = params.getTextDocument().getLanguageId();
        if(languageClients.containsKey(language)){
            return languageClients.get(language).didOpenRequest(params);
        }
        LanguageServerClient newclient = createNewClient(language);
        if(newclient == null) return null;
        return newclient.didOpenRequest(params);
    }

    public void shutdown(){
        for (Process p : this.languageServersProcess.values()){
            p.destroy();
            System.err.println("process killed");
        }
        for (LanguageServerClient client : this.languageClients.values()) {
            client.shutdown();
            client.interrupt();
        }
    }

    public LanguageServerClient createNewClient(String language){
        System.err.println(this.languageServer.properties.ls);
        PolyglotLanguageServerProperties.LanguageServerInfo lsInfo = this.languageServer.getLanguageInfo(language);
        if(lsInfo == null){
            System.err.println("No language Server configured for the language : "+language);
            return null;
        }
        if(this.initializeConnection(lsInfo)){
            LanguageServerClient client = new LanguageServerClient(lsInfo.language, lsInfo.ip, lsInfo.port, this.languageServer);
            this.languageClients.put(language, client);
            client.start();
            if(client.connect()){
                return client;
            }
        }
        return null;
    }

    public boolean initializeConnection(PolyglotLanguageServerProperties.LanguageServerInfo lsInfo){
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
            ProcessBuilder builder = new ProcessBuilder(lsInfo.command);
            Process process = builder.inheritIO().start();
            this.languageServersProcess.put(lsInfo.language, process);
            return true;
        } catch (IOException e) {
            System.err.println(e);
            return false;
        }
    }



}
