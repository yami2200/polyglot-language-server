import com.google.common.collect.ImmutableList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class PolyglotDiagnosticsHandler {

    private PolyglotLanguageServer languageServer;

    private HashMap<String, HashSet<Diagnostic>> diagnostics;

    public PolyglotDiagnosticsHandler(PolyglotLanguageServer languageServer){
        this.languageServer = languageServer;
        diagnostics = new HashMap<String, HashSet<Diagnostic>>();
    }

    public void addDiagnostics(String uri, HashSet<Diagnostic> diagnostics) {
        if (diagnostics!=null) {
            if(this.diagnostics.containsKey(uri)){
                this.diagnostics.get(uri).addAll(diagnostics);
            } else {
                this.diagnostics.put(uri, diagnostics);
            }
        }
    }

    public void publishDiagnostics() {
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null) {
            for(String uri : this.diagnostics.keySet()){
                PublishDiagnosticsParams params = new PublishDiagnosticsParams();
                params.setUri(uri);
                params.setDiagnostics(ImmutableList.copyOf(this.diagnostics.get(uri)));
                client.publishDiagnostics(params);
            }
        }
    }

    public void clearDiagnostics(HashSet<String> URIs){
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null) {
            for(String uri : URIs){
                if(this.diagnostics.containsKey(uri)){
                    client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>(0)));
                    this.diagnostics.remove(uri);
                }
            }
        }
    }

    public void clearDiagnostics(){
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null) {
            for(String uri : this.diagnostics.keySet()){
                client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>(0)));
            }
        }
        this.diagnostics.clear();
    }

}
