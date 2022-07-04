//import com.example.polyglotast.PolyglotDUBuilder;
import com.example.polyglotast.FileNotFoundInfo;
import com.example.polyglotast.PolyglotDUBuilder;
import com.example.polyglotast.PolyglotTreeHandler;
//import com.example.polyglotast.PolyglotTreeProcessor;
import com.example.polyglotast.PolyglotTreePrinter;
import com.google.common.collect.ImmutableList;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PolyglotTextDocumentService implements TextDocumentService {

    private PolyglotLanguageServer languageServer;
    private LSClientLogger clientLogger;

    private PolyglotDiagnosticsHandler diagHandler;

    private String documentLanguage = "none";

    private PolyglotTreeHandler appPolyglotTree = null;

    public PolyglotTextDocumentService(PolyglotLanguageServer languageServer) {
        this.languageServer = languageServer;
        this.clientLogger = LSClientLogger.getInstance();
        this.diagHandler = new PolyglotDiagnosticsHandler(this.languageServer);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didOpen" + "' {fileUri: '" + didOpenTextDocumentParams.getTextDocument().getUri() + "'} opened");

        this.updateTree(didOpenTextDocumentParams.getTextDocument().getUri());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams didChangeTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didChange" + "' {fileUri: '" + didChangeTextDocumentParams.getTextDocument().getUri() + "'} Changed");
    }

    @Override
    public void didClose(DidCloseTextDocumentParams didCloseTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didClose" + "' {fileUri: '" + didCloseTextDocumentParams.getTextDocument().getUri() + "'} Closed");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didSave" + "' {fileUri: '" + didSaveTextDocumentParams.getTextDocument().getUri() + "'} Saved");

        //this.diagHandler.clearDiagnostics();
        this.updateTree(didSaveTextDocumentParams.getTextDocument().getUri());
    }

    public void updateTree(String uri){
        PolyglotFileInfo fileInfo = new PolyglotFileInfo(uri);
        if(fileInfo.isHost()){
            this.clientLogger.logMessage("Host File opened");
            if(!fileInfo.getLanguage().equals("none")){
                try {
                    this.diagHandler.clearDiagnostics();
                    PolyglotTreeHandler.resetFilePathOfTrees();

                    appPolyglotTree = new PolyglotTreeHandler(Paths.get(new URI(fileInfo.getUri())), fileInfo.getLanguage());

                    this.checkFileNotFound();
                    this.checkInconsistencies(fileInfo.getUri());

                    this.diagHandler.publishDiagnostics();
                } catch (Exception e) {
                    System.err.println("Error : "+e.getMessage());
                }
            }
        }
    }

    public void checkFileNotFound(){
        HashMap<Path, HashSet<FileNotFoundInfo>> filesNotFound = PolyglotTreeHandler.getGlobalFilesNotFound();
        for(Path path : filesNotFound.keySet()){
            HashSet<Diagnostic> listDiag = new HashSet<>();
            for(FileNotFoundInfo file : filesNotFound.get(path)){
                Diagnostic diagnostic = new Diagnostic();
                diagnostic.setMessage("File not found");
                diagnostic.setSource("Polyglot");
                diagnostic.setRange(new Range(new Position(file.getLine_position(),file.getChar_position()), new Position(file.getLine_position(), file.getChar_position()+PolyglotLanguagesLib.getUnderlineLengthNotFoundFile(file.getOriginLanguage())+file.getFileName().length())));
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                listDiag.add(diagnostic);
            }
            this.diagHandler.addDiagnostics(path.toUri().toString(), listDiag);
        }
    }

    public void checkInconsistencies(String uri){
        PolyglotDUBuilder Du = new PolyglotDUBuilder();
        appPolyglotTree.apply(Du);
        HashMap<String, LinkedList<kotlin.Pair<Integer, Integer>>> map = Du.getImportInconsistencies();
        HashSet<Diagnostic> listDiag = new HashSet<>();
        for(String s : map.keySet()){
            for(kotlin.Pair<Integer, Integer> p : map.get(s)){
                Diagnostic diagnostic = new Diagnostic();
                diagnostic.setMessage(s+" : variable was imported but never exported");
                diagnostic.setSource("Polyglot");
                diagnostic.setRange(new Range(new Position(p.component1(),0), new Position(p.component1(), Integer.MAX_VALUE)));
                diagnostic.setSeverity(DiagnosticSeverity.Warning);
                listDiag.add(diagnostic);
            }
        }
        this.diagHandler.addDiagnostics(uri, listDiag);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        return CompletableFuture.supplyAsync(() -> {
            this.clientLogger.logMessage("Operation '" + "text/completion");
            CompletionItem completionItem = new CompletionItem();
            completionItem.setLabel("import oui");
            completionItem.setInsertText("var oui = polyglot.import(\"oui\")");
            completionItem.setDetail("Polyglot Import");
            completionItem.setKind(CompletionItemKind.Snippet);
            return Either.forLeft(Arrays.asList(completionItem));
        });
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return TextDocumentService.super.hover(params);
    }

/*@Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        System.err.println("Trigger Hover");
        return CompletableFuture.supplyAsync(() -> {
            Hover hov = new Hover();
            MarkupContent c = new MarkupContent();
            c.setValue("test value");
            c.setKind("test");
            hov.setContents(c);
            Range r = new Range();
            r.setStart(new Position(1,1));
            r.setEnd(new Position(1,10));
            hov.setRange(r);
            return hov;
        });
    }*/

}
