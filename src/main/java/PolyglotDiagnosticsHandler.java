import com.google.common.collect.ImmutableList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PolyglotDiagnosticsHandler {

    private PolyglotLanguageServer languageServer;

    private HashMap<String, HashMap<DiagnosticCategory, HashSet<Diagnostic>>> diagnostics;

    public PolyglotDiagnosticsHandler(PolyglotLanguageServer languageServer){
        this.languageServer = languageServer;
        diagnostics = new HashMap<String, HashMap<DiagnosticCategory, HashSet<Diagnostic>>>();
    }

    public void addDiagnostic(String uri, Diagnostic diagnostic, DiagnosticCategory category){
        if (diagnostics!=null && !category.equals(DiagnosticCategory.ALL)) {
            if(this.diagnostics.containsKey(uri)){
                if(this.diagnostics.get(uri).containsKey(category)) {
                    this.diagnostics.get(uri).get(category).add(diagnostic);
                } else {
                    HashSet<Diagnostic> set = new HashSet<Diagnostic>();
                    set.add(diagnostic);
                    this.diagnostics.get(uri).put(category, set);
                }
            } else {
                HashMap<DiagnosticCategory, HashSet<Diagnostic>> map = new HashMap<DiagnosticCategory, HashSet<Diagnostic>>();
                HashSet<Diagnostic> set = new HashSet<Diagnostic>();
                set.add(diagnostic);
                map.put(category, set);
                this.diagnostics.put(uri, map);
            }
        }
    }

    public void publishDiagnostics(String uri){
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null && this.diagnostics.containsKey(uri)) {
            HashSet<Diagnostic> list = new HashSet<>();
            for (HashSet<Diagnostic> value : this.diagnostics.get(uri).values()) {
                list.addAll(value);
            }
            PublishDiagnosticsParams params = new PublishDiagnosticsParams();
            params.setUri(uri);
            params.setDiagnostics(ImmutableList.copyOf(list));
            client.publishDiagnostics(params);
        }
    }

    public void publishDiagnostics(HashSet<String> uris){
        for (String s : uris) {
            this.publishDiagnostics(s);
        }
    }

    public void clearDiagnostics(String uri, DiagnosticCategory category){
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null && this.diagnostics.containsKey(uri)) {
            if(category.equals(DiagnosticCategory.ALL)){
                this.clearDiagnostics(uri, DiagnosticCategory.FILENOTFOUND);
                this.clearDiagnostics(uri, DiagnosticCategory.IMPORTEXPORT);
            } else {
                this.diagnostics.get(uri).remove(category);
            }
        }
    }

    public void clearDiagnostics(HashSet<String> URIs){
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null) {
            for(String uri : URIs){
                if(this.diagnostics.containsKey(uri)){
                    client.publishDiagnostics(new PublishDiagnosticsParams(uri, new ArrayList<>(0)));
                }
            }
        }
    }

}

