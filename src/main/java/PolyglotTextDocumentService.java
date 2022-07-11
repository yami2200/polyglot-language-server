//import com.example.polyglotast.PolyglotDUBuilder;
import com.example.polyglotast.*;
//import com.example.polyglotast.PolyglotTreeProcessor;
import com.example.polyglotast.utils.ExportData;
import com.example.polyglotast.utils.ExportImportStep;
import com.example.polyglotast.utils.FileNotFoundInfo;
import com.example.polyglotast.utils.ImportData;
import com.google.common.collect.ImmutableList;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PolyglotTextDocumentService implements TextDocumentService {

    private PolyglotLanguageServer languageServer;
    private LSClientLogger clientLogger;
    private PolyglotDiagnosticsHandler diagHandler;



    public PolyglotTextDocumentService(PolyglotLanguageServer languageServer) {
        this.languageServer = languageServer;
        this.clientLogger = LSClientLogger.getInstance();
        this.diagHandler = new PolyglotDiagnosticsHandler(this.languageServer);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didOpen" + "' {fileUri: '" + didOpenTextDocumentParams.getTextDocument().getUri() + "'} opened");

        String uri = didOpenTextDocumentParams.getTextDocument().getUri();
        Path path;
        try {
            path = Paths.get(new URI(uri));
        } catch (Exception e) {
            return;
        }
        if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
            try {
                this.changeTree(uri, Files.readString(path));
            } catch (Exception e) {
            }
        } else {
            try {
                this.createTree(uri);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams didChangeTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didChange" + "' {fileUri: '" + didChangeTextDocumentParams.getTextDocument().getUri() + "'} Changed");

        String uri = didChangeTextDocumentParams.getTextDocument().getUri();
        Path path;
        try {
            path = Paths.get(new URI(uri));
        } catch (Exception e) {
            return;
        }

        if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
            if(didChangeTextDocumentParams.getContentChanges().size()>0 && didChangeTextDocumentParams.getContentChanges().get(0).getRange() == null){
                try {
                    this.changeTree(uri, didChangeTextDocumentParams.getContentChanges().get(0).getText());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            try {
                this.createTree(uri);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public void didClose(DidCloseTextDocumentParams didCloseTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didClose" + "' {fileUri: '" + didCloseTextDocumentParams.getTextDocument().getUri() + "'} Closed");
    }

    @Override
    public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didSave" + "' {fileUri: '" + didSaveTextDocumentParams.getTextDocument().getUri() + "'} Saved");

        String uri = didSaveTextDocumentParams.getTextDocument().getUri();
        Path path;
        try {
            path = Paths.get(new URI(uri));
        } catch (Exception e) {
            return;
        }

        if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
            try {
                this.changeTree(uri, Files.readString(path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                this.createTree(uri);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void changeTree(String uri, String newCode) throws URISyntaxException {
        Path path = Paths.get(new URI(uri));
        if(!PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)) return;
        PolyglotTreeHandler tree_changed = PolyglotTreeHandler.getfilePathToTreeHandler().get(path);
        tree_changed.reparsePolyglotTree(newCode);

        HashSet<Path> paths = new HashSet<>();
        paths.add(path);
        for (PolyglotTreeHandler subTree : tree_changed.getSubTrees()) {
            if(PolyglotTreeHandler.getfilePathOfTreeHandler().containsKey(subTree)) paths.add(PolyglotTreeHandler.getfilePathOfTreeHandler().get(subTree));
        }
        this.checkFileNotFound(paths);

        for (Path pathSubTrees : paths) {
            this.diagHandler.publishDiagnostics(pathSubTrees.toUri().toString());
        }
    }

    public void createTree(String uri) throws URISyntaxException, IOException {
        Path path = Paths.get(new URI(uri));
        if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)) return;
        String[] splitURI = uri.split("[.]", 0);
        String language = getLanguageFromExtension(splitURI[splitURI.length-1]);
        if(language.equals("none")) return;
        PolyglotTreeHandler newTree = new PolyglotTreeHandler(path, language);

        this.createTreesFromDirectory(path.getParent().toString());

        HashSet<Path> paths = new HashSet<>();
        for (PolyglotTreeHandler hostTree : newTree.getHostTrees()) {
            if(PolyglotTreeHandler.getfilePathOfTreeHandler().containsKey(hostTree)) paths.add(PolyglotTreeHandler.getfilePathOfTreeHandler().get(hostTree));
            for (PolyglotTreeHandler subTree : hostTree.getSubTrees()) {
                if(PolyglotTreeHandler.getfilePathOfTreeHandler().containsKey(subTree)) paths.add(PolyglotTreeHandler.getfilePathOfTreeHandler().get(subTree));
            }
        }
        this.checkFileNotFound(paths);

        for (Path pathSubTrees : paths) {
            this.diagHandler.publishDiagnostics(pathSubTrees.toUri().toString());
        }
    }

    public void createTreesFromDirectory(String filePath) throws IOException {
        File dir = new File(filePath);
        if (dir.exists() && dir.isDirectory()) {
            File files[] = dir.listFiles();
            for (File file : files) {
                Path path = Paths.get(file.getAbsolutePath());
                if(!PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
                    String[] splitURI = path.toString().split("[.]", 0);
                    String language = getLanguageFromExtension(splitURI[splitURI.length-1]);
                    if(!language.equals("none")){
                        PolyglotTreeHandler newTree = new PolyglotTreeHandler(path, language);
                        System.err.println("Tree created : "+path);
                    }
                }
            }
        }
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

    private String getLanguageFromExtension(String extension){
        switch (extension){
            case "js":
                return "javascript";
            case "py":
                return "python";
            default:
                return "none";
        }
    }

    public void checkFileNotFound(HashSet<Path> paths){
        HashMap<Path, HashSet<FileNotFoundInfo>> filesNotFound = new HashMap<>();

        for(Path path : paths){
            this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.FILENOTFOUND);
            if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
                PolyglotTreeHandler tree = PolyglotTreeHandler.getfilePathToTreeHandler().get(path);
                for(FileNotFoundInfo fileNotFound : tree.getFilesNotFound()){
                    if(filesNotFound.containsKey(path)){
                        filesNotFound.get(path).add(fileNotFound);
                    } else {
                        HashSet<FileNotFoundInfo> set = new HashSet<FileNotFoundInfo>();
                        set.add(fileNotFound);
                        filesNotFound.put(path, set);
                    }
                }
            }
        }

        for(Path path : filesNotFound.keySet()){
            for(FileNotFoundInfo file : filesNotFound.get(path)){
                Diagnostic diagnostic = new Diagnostic();
                diagnostic.setMessage("File not found : "+file.getFileName());
                diagnostic.setSource("Polyglot EvalFile");
                diagnostic.setRange(new Range(new Position(file.getLine_position(),file.getChar_position()), new Position(file.getLine_position_end(), file.getChar_position_end())));
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                this.diagHandler.addDiagnostic(path.toUri().toString(), diagnostic, DiagnosticCategory.FILENOTFOUND);
            }
        }
    }

    public void checkInconsistencies(PolyglotTreeHandler hostTree){
        PolyglotDUBuilder du = new PolyglotDUBuilder();
        hostTree.apply(du);
        for (Path path : du.getPathsCovered()) {
            this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.IMPORTEXPORT);
        }
        for (ExportImportStep exportImportStep : du.getListOperation()) {

        }
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

    /*public void checkInconsistencies(String uri){
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

        HashMap<String, kotlin.Pair<Integer, Integer>> map_exp = Du.getExportInconsistencies();
        for(String s : map_exp.keySet()){
            kotlin.Pair<Integer, Integer> p = map_exp.get(s);
            Diagnostic diagnostic = new Diagnostic();
            diagnostic.setMessage(s+" : variable was exported but never imported");
            diagnostic.setSource("Polyglot");
            diagnostic.setRange(new Range(new Position(p.component1(),0), new Position(p.component1(), Integer.MAX_VALUE)));
            diagnostic.setSeverity(DiagnosticSeverity.Information);
            listDiag.add(diagnostic);
        }

        this.diagHandler.addDiagnostics(uri, listDiag);
    }*/


    /*
    public void createTree(String uri) throws URISyntaxException, IOException {
        PolyglotFileInfo fileInfo = new PolyglotFileInfo(uri);
        if(fileInfo.isHost()){
            this.clientLogger.logMessage("Host File opened");
            if(!fileInfo.getLanguage().equals("none")){
                try {
                    this.diagHandler.clearDiagnostics();
                    //PolyglotTreeHandler.resetFilePathOfTrees();

                    appPolyglotTree = new PolyglotTreeHandler(Paths.get(new URI(fileInfo.getUri())), fileInfo.getLanguage());

                    this.checkFileNotFound();
                    this.checkInconsistencies(fileInfo.getUri());

                    this.diagHandler.publishDiagnostics();
                } catch (Exception e) {
                    System.err.println("Error : "+e.getMessage());
                }
            }
        }
    }*/
}
