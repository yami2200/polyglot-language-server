import com.example.polyglotast.PolyglotTreeHandler;
import com.google.common.collect.ImmutableList;
import kotlin.Pair;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class PolyglotDiagnosticsHandler {

    private PolyglotLanguageServer languageServer; // Reference to the Language Server

    private HashMap<String, HashMap<Pair<DiagnosticCategory, Path>, HashSet<Diagnostic>>> diagnostics; // Map which stored all the diagnostics

    public PolyglotDiagnosticsHandler(PolyglotLanguageServer languageServer){
        this.languageServer = languageServer;
        this.diagnostics = new HashMap<>();
    }

    /**
     * Add diagnostic to the diagnostics map, which could be sent later to the client with "publishDiagnostics"
     * @param uri URI of the file of the diagnostic
     * @param diagnostic Diagnostic object
     * @param category Category of the diagnostic
     * @param hostPath Path of Host File which triggered this diagnostic (useful for multihost polyglot program)
     */
    public void addDiagnostic(String uri, Diagnostic diagnostic, DiagnosticCategory category, Path hostPath){
        if (diagnostics!=null && !category.equals(DiagnosticCategory.ALL)) {
            if(this.diagnostics.containsKey(uri)){
                if(this.diagnostics.get(uri).containsKey(new Pair(category, hostPath))) {
                    this.diagnostics.get(uri).get(new Pair(category, hostPath)).add(diagnostic);
                } else {
                    HashSet<Diagnostic> set = new HashSet<Diagnostic>();
                    set.add(diagnostic);
                    this.diagnostics.get(uri).put(new Pair(category, hostPath), set);
                }
            } else {
                HashMap<Pair<DiagnosticCategory, Path>, HashSet<Diagnostic>> map = new HashMap<>();
                HashSet<Diagnostic> set = new HashSet<Diagnostic>();
                set.add(diagnostic);
                map.put(new Pair(category, hostPath), set);
                this.diagnostics.put(uri, map);
            }
        }
    }

    /**
     * Publish all diagnostics stored for a specific file, to the client
     * @param uri file's uri
     */
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

    /**
     * Publish all diagnostics stored for a set of files, to the client
     * @param uris set of file's URI
     */
    public void publishDiagnostics(HashSet<String> uris){
        for (String s : uris) {
            this.publishDiagnostics(s);
        }
    }

    /**
     * Clear all diagnostics stored from a specific file & category (diagnostics are not removed from client with this function)
     * @param uri file's uri
     * @param category diagnostic category to remove
     * @param hostPath file host who owns the diagnostics
     */
    public void clearDiagnostics(String uri, DiagnosticCategory category, Path hostPath){
        LanguageClient client = this.languageServer.languageClient;
        if (client!=null && this.diagnostics!=null && this.diagnostics.containsKey(uri)) {
            if(category.equals(DiagnosticCategory.ALL)){
                this.clearDiagnostics(uri, DiagnosticCategory.FILENOTFOUND, hostPath);
                this.clearDiagnostics(uri, DiagnosticCategory.IMPORTEXPORT, hostPath);
            } else {
                this.diagnostics.get(uri).remove(new Pair(category, hostPath));
            }
        }
    }

    /**
     * Clear all diagnostics stored from many files
     * @param URIs set of file's URI
     */
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

