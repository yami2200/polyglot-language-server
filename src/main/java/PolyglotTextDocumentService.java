import com.example.polyglotast.*;
import com.example.polyglotast.utils.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
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

        // CHECK ERROR NOT FOUND ERRORS
        HashSet<Path> paths = new HashSet<>();
        paths.add(path);
        for (PolyglotTreeHandler subTree : tree_changed.getSubTrees()) {
            if(PolyglotTreeHandler.getfilePathOfTreeHandler().containsKey(subTree)) paths.add(PolyglotTreeHandler.getfilePathOfTreeHandler().get(subTree));
        }
        this.checkFileNotFound(paths);

        // CHECK IMPORT / EXPORT ERRORS
        paths.addAll(this.checkInconsistencies(tree_changed.getHostTrees()));

        for (Path pathUpdatedTree : paths) {
            this.diagHandler.publishDiagnostics(pathUpdatedTree.toUri().toString());
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

        HashSet<PolyglotTreeHandler> hostTrees = newTree.getHostTrees();

        // CHECK ERROR NOT FOUND ERRORS
        HashSet<Path> paths = new HashSet<>();
        for (PolyglotTreeHandler hostTree : hostTrees) {
            if(PolyglotTreeHandler.getfilePathOfTreeHandler().containsKey(hostTree)) paths.add(PolyglotTreeHandler.getfilePathOfTreeHandler().get(hostTree));
            for (PolyglotTreeHandler subTree : hostTree.getSubTrees()) {
                if(PolyglotTreeHandler.getfilePathOfTreeHandler().containsKey(subTree)) paths.add(PolyglotTreeHandler.getfilePathOfTreeHandler().get(subTree));
            }
        }
        this.checkFileNotFound(paths);

        // CHECK IMPORT / EXPORT ERRORS
        paths.addAll(this.checkInconsistencies(hostTrees));

        for (Path pathUpdatedTree : paths) {
            this.diagHandler.publishDiagnostics(pathUpdatedTree.toUri().toString());
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
            ArrayList<CompletionItem> listItems = new ArrayList<>();
            try {
                this.checkCompletion(position, listItems);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return Either.forLeft(listItems);
        });
    }

    public void checkCompletion(CompletionParams position, ArrayList<CompletionItem> listCompletions) throws URISyntaxException {
        // Check Import Variable
        if(position.getPosition().getCharacter() == 0){
            PolyglotTreeHandler currentTree = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(position.getTextDocument().getUri())));
            if(currentTree == null) return;
            PolyglotVariableSpotter varSpotter = new PolyglotVariableSpotter();
            for (PolyglotTreeHandler hostTree : currentTree.getHostTrees()) {
                hostTree.apply(varSpotter);
            }
            Set<String> listVarNotImported = new HashSet<String>(varSpotter.getExports().keySet());
            for (String var : varSpotter.getExports().keySet()) {
                if((varSpotter.getImports().containsKey(var) && varSpotter.getImports().get(var).containsKey(currentTree) && varSpotter.getImports().get(var).get(currentTree).size() > 0) || varSpotter.getExports().get(var).containsKey(currentTree)) listVarNotImported.remove(var);
            }
            for (String var : listVarNotImported) {
                CompletionItem completionItem = new CompletionItem();
                completionItem.setLabel(var);
                switch (currentTree.getLang()){
                    case "python":
                        completionItem.setInsertText(var+" = polyglot.import_value(name=\""+var+"\")\r\n");
                        break;
                    case "js":
                    case "javascript":
                        completionItem.setInsertText("let "+var+" = Polyglot.import(\""+var+"\");\r\n");
                        break;
                }

                completionItem.setDetail("Polyglot Import "+var);
                completionItem.setKind(CompletionItemKind.Variable);
                listCompletions.add(completionItem);
            }
        }
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        this.clientLogger.logMessage("Operation '" + "text/codeAction" + "' {fileUri: '" + params.getTextDocument().getUri() + "'} Code Action");
        return TextDocumentService.super.codeAction(params);
    }

    @Override
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
        this.clientLogger.logMessage("Operation '" + "text/resolveCodeAction" + "' {title: '" + unresolved.getTitle() + "'} Resolve Code Action");
        return TextDocumentService.super.resolveCodeAction(unresolved);
    }

    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
        this.clientLogger.logMessage("Operation '" + "text/Diagnostic" + "' {title: '" + params.getTextDocument().getUri() + "'} Diagnostic");
        return TextDocumentService.super.diagnostic(params);
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
            this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.FILENOTFOUND, path);
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
                this.diagHandler.addDiagnostic(path.toUri().toString(), diagnostic, DiagnosticCategory.FILENOTFOUND, path);
            }
        }
    }

    public HashSet<Path> checkInconsistencies(HashSet<PolyglotTreeHandler> trees){
        HashSet<Path> pathsCovered = new HashSet<>();
        HashSet<String> diagnosticsID = new HashSet<>();
        for (PolyglotTreeHandler tree : trees) {
            PolyglotDUBuilder du = new PolyglotDUBuilder();
            tree.apply(du);

            for (Path path : du.getPathsCovered()) {
                this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.IMPORTEXPORT, PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree));
                if(!pathsCovered.contains(path)){
                    pathsCovered.add(path);
                    this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.IMPORTEXPORT, path);
                }
            }
            for (String s : du.getExportWithoutImport().keySet()) {
                for (ExportData exportData : du.getExportWithoutImport().get(s)) {
                    if(!diagnosticsID.contains(exportData.getId())){
                        diagnosticsID.add(exportData.getId());
                        this.addImportExportDiagnostic("Variable \""+ exportData.getVar_name()+"\" is exported but never imported.",
                                "Polyglot Unused Export", DiagnosticSeverity.Information, exportData.getFilePath(),
                                new Range(new Position(exportData.getLine_pos(), exportData.getChar_pos()), new Position(exportData.getLine_pos_end(), exportData.getChar_pos_end())),
                                exportData.getFilePath());
                    }
                }
            }

            for (String s : du.getImportBeforeExport().keySet()) {
                for (ImportData importData : du.getImportBeforeExport().get(s)) {
                    this.addImportExportDiagnostic("Variable \""+ importData.getVar_name()+"\" is imported before the export statement. (With Host : "+PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree).getFileName().toString()+")",
                            "Polyglot Import Order Warning", DiagnosticSeverity.Warning, importData.getFilePath(),
                            new Range(new Position(importData.getLine_pos(), importData.getChar_pos()), new Position(importData.getLine_pos_end(), importData.getChar_pos_end())),
                            PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree));
                }
            }

            for (String s : du.getImportWithoutExport().keySet()) {
                for (ImportData importData : du.getImportWithoutExport().get(s)) {
                    this.addImportExportDiagnostic("Variable \""+ importData.getVar_name()+"\" is imported but is never exported. (With Host : "+PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree).getFileName().toString()+")",
                            "Polyglot Undefined Import", DiagnosticSeverity.Warning, importData.getFilePath(),
                            new Range(new Position(importData.getLine_pos(), importData.getChar_pos()), new Position(importData.getLine_pos_end(), importData.getChar_pos_end())),
                            PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree));
                }
            }

            for (String s : du.getImportFromSameFile().keySet()) {
                for (ImportData importData : du.getImportFromSameFile().get(s)) {
                    if(!diagnosticsID.contains(importData.getId())) {
                        diagnosticsID.add(importData.getId());
                        this.addImportExportDiagnostic("Useless Variable Import. Variable \"" + importData.getVar_name() + "\" is imported from the same file it was exported.",
                                "Polyglot Useless Import", DiagnosticSeverity.Information, importData.getFilePath(),
                                new Range(new Position(importData.getLine_pos(), importData.getChar_pos()), new Position(importData.getLine_pos_end(), importData.getChar_pos_end())),
                                importData.getFilePath());
                    }
                }
            }

            for (CodeArea codeArea : du.getEvalSameFile()) {
                if(!diagnosticsID.contains("evalSameFile"+codeArea.id)) {
                    diagnosticsID.add("evalSameFile"+codeArea.id);
                    this.addImportExportDiagnostic("Dangerous file eval. You are evaluating the same file you are currently in.",
                            "Polyglot Eval", DiagnosticSeverity.Error, codeArea.filePath,
                            new Range(new Position(codeArea.line_pos, codeArea.char_pos), new Position(codeArea.line_pos_end, codeArea.char_pos_end)),
                            codeArea.filePath);
                }
            }


        }
        return pathsCovered;
    }

    private void addImportExportDiagnostic(String message, String source, DiagnosticSeverity severity, Path path, Range range, Path diagOwner){
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setMessage(message);
        diagnostic.setSource(source);
        diagnostic.setRange(range);
        diagnostic.setSeverity(severity);
        this.diagHandler.addDiagnostic(path.toUri().toString(), diagnostic, DiagnosticCategory.IMPORTEXPORT, diagOwner);
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
