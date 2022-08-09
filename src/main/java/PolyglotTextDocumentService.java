import com.example.polyglotast.*;
import com.example.polyglotast.utils.*;
import kotlin.Pair;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PolyglotTextDocumentService implements TextDocumentService {

    private PolyglotLanguageServer languageServer; // Reference to the language Server
    private LSClientLogger clientLogger; // Reference to the instance of clientLogger
    private PolyglotDiagnosticsHandler diagHandler; // Reference to diagnostic Handler
    private HashSet<Path> externLSOpenedPaths; // Set of all paths that have been opened in extern Language Server (Used to avoid duplicated open request)



    public PolyglotTextDocumentService(PolyglotLanguageServer languageServer) {
        this.languageServer = languageServer;
        this.clientLogger = LSClientLogger.getInstance();
        this.diagHandler = new PolyglotDiagnosticsHandler(this.languageServer);
        this.externLSOpenedPaths = new HashSet<>();
    }

    /**
     * ################################## FILE SYNCHRONIZATION & AST PARSING ###########################################
     */

    /**
     * LSP didOpen Request Handler
     * @param didOpenTextDocumentParams DidOpenTextDocumentParams
     */
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
                System.err.println(e.getMessage());
            }
        } else {
            try {
                this.createTree(uri);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * LSP didChange Request Handler
     * @param didChangeTextDocumentParams DidChangeTextDocumentParams
     */
    @Override
    public void didChange(DidChangeTextDocumentParams didChangeTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didChange" + "' {fileUri: '" + didChangeTextDocumentParams.getTextDocument().getUri() + "'} Changed");
        this.languageServer.languageClientManager.didChangeRequest(didChangeTextDocumentParams);

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

    /**
     * LSP didClose Request Handler
     * @param didCloseTextDocumentParams DidCloseTextDocumentParams
     */
    @Override
    public void didClose(DidCloseTextDocumentParams didCloseTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didClose" + "' {fileUri: '" + didCloseTextDocumentParams.getTextDocument().getUri() + "'} Closed");
    }

    /**
     * LSP didSave Request Handler
     * @param didSaveTextDocumentParams DidSaveTextDocumentParams
     */
    @Override
    public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams) {
        this.clientLogger.logMessage("Operation '" + "text/didSave" + "' {fileUri: '" + didSaveTextDocumentParams.getTextDocument().getUri() + "'} Saved");
        this.languageServer.languageClientManager.didSaveRequest(didSaveTextDocumentParams);

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

    /**
     * Update AST of specific file with the new code, and update all diagnostics related to the edition
     * @param uri uri of file changed
     * @param newCode new code of the file
     */
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

    /**
     * Parse a new Polyglot AST from uri, parse all files in directory and process all diagnostics for all these files
     * @param uri Uri of the file to parse
     */
    public void createTree(String uri) throws URISyntaxException, IOException {
        // Check if the file has not been parsed before
        Path path = Paths.get(new URI(uri));
        if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)) return;
        // Get language of file
        String[] splitURI = uri.split("[.]", 0);
        String language = getLanguageFromExtension(splitURI[splitURI.length-1]);
        if(language.equals("none")) return;
        // Parse the file into a Polyglot AST
        PolyglotTreeHandler newTree = new PolyglotTreeHandler(path, language);

        this.createTreesFromDirectory(path.getParent().toString());

        HashSet<PolyglotTreeHandler> hostTrees = newTree.getHostTrees();

        // CHECK "ERROR NOT FOUND" ERRORS
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
            // PUBLISH DIAGNOSTICS
            this.diagHandler.publishDiagnostics(pathUpdatedTree.toUri().toString());

            // SEND REQUEST TO LANGUAGE SERVER FOR THE SPECIFIC LANGUAGE
            this.sendDidOpenRequestToLanguageServers(pathUpdatedTree);
        }
    }

    /**
     * Parse all Files into Polyglot ASTs from the same directory of the file passed in parameter
     * @param filePath file directory to look in
     */
    public void createTreesFromDirectory(String filePath) throws IOException {
        File dir = new File(filePath);
        if (dir.exists() && dir.isDirectory()) {
            // Get all files of directory
            File files[] = dir.listFiles();
            for (File file : files) {
                // Check if the path has not been already parsed
                Path path = Paths.get(file.getAbsolutePath());
                if(!PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
                    // Check if it's a correct file extension handled by the server
                    String[] splitURI = path.toString().split("[.]", 0);
                    String language = getLanguageFromExtension(splitURI[splitURI.length-1]);
                    if(!language.equals("none")){
                        // Parse the file into a PolyglotTree and sendOpen request to the proper Language Server
                        PolyglotTreeHandler newTree = new PolyglotTreeHandler(path, language);
                        this.sendDidOpenRequestToLanguageServers(path);
                        for (PolyglotTreeHandler subTree : newTree.getSubTrees()) {
                            this.sendDidOpenRequestToLanguageServers(PolyglotTreeHandler.getfilePathOfTreeHandler().get(subTree));
                        }
                        this.clientLogger.logMessage("Tree created at path : "+path);
                    }
                }
            }
        }
    }

    /**
     * Send didOpen Request to specific Language Server of File Language Programming
     * @param path file opened
     */
    public void sendDidOpenRequestToLanguageServers(Path path) throws IOException {
        if(this.externLSOpenedPaths.contains(path)) return;
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        TextDocumentItem tdi = new TextDocumentItem();
        tdi.setVersion(1);
        tdi.setLanguageId(PolyglotTreeHandler.getfilePathToTreeHandler().get(path).getLang());
        tdi.setUri(path.toUri().toString());
        tdi.setText(Files.readString(path));
        params.setTextDocument(tdi);
        this.externLSOpenedPaths.add(path);
        this.languageServer.languageClientManager.didOpenRequest(params);
    }

    /**
     * ################################################# DIAGNOSTICS ###################################################
     */

    /**
     * Add diagnostics to diagnostic Handler for each file not found of files in paths
     * @param paths set of file paths to look in
     */
    public void checkFileNotFound(HashSet<Path> paths){
        HashMap<Path, HashSet<FileNotFoundInfo>> filesNotFound = new HashMap<>();

        // Put all files not found from all trees at specific path in the map
        for(Path path : paths){
            this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.FILENOTFOUND, path);
            if(PolyglotTreeHandler.getfilePathToTreeHandler().containsKey(path)){
                PolyglotTreeHandler tree = PolyglotTreeHandler.getfilePathToTreeHandler().get(path);
                for(FileNotFoundInfo fileNotFound : tree.getFilesNotFound()){
                    if(filesNotFound.containsKey(path)){
                        filesNotFound.get(path).add(fileNotFound);
                    } else {
                        HashSet<FileNotFoundInfo> set = new HashSet<>();
                        set.add(fileNotFound);
                        filesNotFound.put(path, set);
                    }
                }
            }
        }

        // Create diagnostic for each element in map
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

    /**
     * Check Inconsistencies/Warning in all trees passed in parameter
     * @param trees AST to check
     * @return all paths checked in
     */
    public HashSet<Path> checkInconsistencies(HashSet<PolyglotTreeHandler> trees){
        HashSet<Path> pathsCovered = new HashSet<>();
        HashSet<String> diagnosticsID = new HashSet<>();
        // Loop through all trees and process all inconsistencies with DUBuilder
        for (PolyglotTreeHandler tree : trees) {
            PolyglotDUBuilder du = new PolyglotDUBuilder();
            tree.apply(du);

            // Loop through all trees processed by DUBuilder to clear their diagnostics
            for (Path path : du.getPathsCovered()) {
                this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.IMPORTEXPORT, PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree));
                // Add the path to pathsCovered if it wasn't in it previously
                if(!pathsCovered.contains(path)){
                    pathsCovered.add(path);
                    this.diagHandler.clearDiagnostics(path.toUri().toString(), DiagnosticCategory.IMPORTEXPORT, path);
                }
            }

            // Information : Export without import
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

            // Warning : Import before the export
            for (String s : du.getImportBeforeExport().keySet()) {
                for (ImportData importData : du.getImportBeforeExport().get(s)) {
                    this.addImportExportDiagnostic("Variable \""+ importData.getVar_name()+"\" is imported before the export statement. (With Host : "+PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree).getFileName().toString()+")",
                            "Polyglot Import Order Warning", DiagnosticSeverity.Warning, importData.getFilePath(),
                            new Range(new Position(importData.getLine_pos(), importData.getChar_pos()), new Position(importData.getLine_pos_end(), importData.getChar_pos_end())),
                            PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree));
                }
            }

            // Warning : Import without an export
            for (String s : du.getImportWithoutExport().keySet()) {
                for (ImportData importData : du.getImportWithoutExport().get(s)) {
                    this.addImportExportDiagnostic("Variable \""+ importData.getVar_name()+"\" is imported but is never exported. (With Host : "+PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree).getFileName().toString()+")",
                            "Polyglot Undefined Import", DiagnosticSeverity.Warning, importData.getFilePath(),
                            new Range(new Position(importData.getLine_pos(), importData.getChar_pos()), new Position(importData.getLine_pos_end(), importData.getChar_pos_end())),
                            PolyglotTreeHandler.getfilePathOfTreeHandler().get(tree));
                }
            }

            // Information : Import & Export from the same file
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

            // Warning : Evaluate the file A into the file A (could cause infinite loop evaluation and so on)
            for (CodeArea codeArea : du.getEvalSameFile()) {
                if(!diagnosticsID.contains("evalSameFile"+codeArea.id)) {
                    diagnosticsID.add("evalSameFile"+codeArea.id);
                    this.addImportExportDiagnostic("Dangerous file eval. You are evaluating the same file you are currently in.",
                            "Polyglot Eval", DiagnosticSeverity.Warning, codeArea.filePath,
                            new Range(new Position(codeArea.line_pos, codeArea.char_pos), new Position(codeArea.line_pos_end, codeArea.char_pos_end)),
                            codeArea.filePath);
                }
            }

        }
        return pathsCovered;
    }

    /**
     * Add Diagnostic in Diagnostic Handler
     * @param message Message of the diagnostic
     * @param source Source of the diagnostic
     * @param severity Severity of the diagnostic (Warning, Info, Error ...)
     * @param path Path of file where is the diagnostic belongs to
     * @param range Range of underline effect
     * @param diagOwner Diagnostic Owner path file (Useful for Multihost diagnostics)
     */
    private void addImportExportDiagnostic(String message, String source, DiagnosticSeverity severity, Path path, Range range, Path diagOwner){
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setMessage(message);
        diagnostic.setSource(source);
        diagnostic.setRange(range);
        diagnostic.setSeverity(severity);
        this.diagHandler.addDiagnostic(path.toUri().toString(), diagnostic, DiagnosticCategory.IMPORTEXPORT, diagOwner);
    }


    /**
     * ################################################# COMPLETION ####################################################
     */

    /**
     * LSP completion Request Handler
     * @param completionParams CompletionParams
     * @return completion list items
     */
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        return CompletableFuture.supplyAsync(() -> {
            this.clientLogger.logMessage("Operation '" + "text/completion");
            ArrayList<CompletionItem> listItems = new ArrayList<>();
            try {
                this.checkCompletion(completionParams, listItems);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            return Either.forLeft(listItems);
        });
    }

    /**
     * Fill the list passed in parameters, with completions items of un-imported polyglot variables
     * @param completionParams CompletionParams
     * @param listCompletions the list to fill with completion items
     */
    public void checkCompletion(CompletionParams completionParams, ArrayList<CompletionItem> listCompletions) throws URISyntaxException {
        // PROTOTYPE : only works when beginning a new line
        if(completionParams.getPosition().getCharacter() == 0){
            // Get the tree of current file & spot all variables export/import
            PolyglotTreeHandler currentTree = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(completionParams.getTextDocument().getUri())));
            if(currentTree == null) return;
            PolyglotVariableSpotter varSpotter = new PolyglotVariableSpotter();
            for (PolyglotTreeHandler hostTree : currentTree.getHostTrees()) {
                hostTree.apply(varSpotter);
            }
            // Create a set of all polyglot variables not imported in the current file
            Set<String> listVarNotImported = new HashSet<String>(varSpotter.getExports().keySet());
            for (String var : varSpotter.getExports().keySet()) {
                if((varSpotter.getImports().containsKey(var) && varSpotter.getImports().get(var).containsKey(currentTree) && varSpotter.getImports().get(var).get(currentTree).size() > 0) || varSpotter.getExports().get(var).containsKey(currentTree)) listVarNotImported.remove(var);
            }
            // Create one completion item for each
            for (String var : listVarNotImported) {
                CompletionItem completionItem = new CompletionItem();
                completionItem.setLabel(var);
                // Set insertText depending on the file programming language
                switch (currentTree.getLang()){
                    case "python":
                        completionItem.setInsertText(var+" = polyglot.import_value(name=\""+var+"\")\r\n");
                        break;
                    case "js":
                    case "javascript":
                        completionItem.setInsertText("let "+var+" = Polyglot.import(\""+var+"\");\r\n");
                        break;
                }
                // Set details of completion item
                completionItem.setDetail("Polyglot Import "+var);
                completionItem.setKind(CompletionItemKind.Variable);
                listCompletions.add(completionItem);
            }
        }
    }

    /**
     * ################################################# HOVER #########################################################
     */

    /**
     * LSP hover Request Handler
     * @param params HoverParams
     * @return hover text
     */
    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        try{
            // Get tree, get the tree node which is hovered, and verify if it's an identifier
            PolyglotTreeHandler tree = PolyglotTreeHandler.getfilePathToTreeHandler().get(Paths.get(new URI(params.getTextDocument().getUri())));
            PolyglotZipper zipper = new PolyglotZipper(tree, tree.getNodeAtPosition(new Pair<>(params.getPosition().getLine(), params.getPosition().getCharacter())));
            if(zipper.node != null && zipper.getType().equals("identifier")){
                // Loop through HostTrees to make typing visit, TODO : Handle multiple results from all Host for typing feature
                HashSet<PolyglotTreeHandler> hostTrees = tree.getHostTrees();
                for (PolyglotTreeHandler hostTree : hostTrees) {
                    // Make analysis for typing
                    PolyglotTypeVisitor typeVisitor = new PolyglotTypeVisitor(zipper);
                    hostTree.apply(typeVisitor);
                    PolyglotTypeVisitor.TypingResult result = typeVisitor.getTypeResult();
                    // The variable was exported directly with a raw value
                    if(result.typeResult.equals(PolyglotTypeVisitor.TypeResultEnum.VALUETYPE)){
                        return CompletableFuture.supplyAsync(() -> {
                            return getHoverObject(zipper, result.type, hostTree, hostTrees.size());
                        });
                        // The variable was exported with a variable
                    } else if (result.typeResult.equals(PolyglotTypeVisitor.TypeResultEnum.EXPORTTYPE)){
                        // Make a hover request to Language Server to get the type of the variable which was used for the export
                        HoverParams newParams = new HoverParams();
                        newParams.setTextDocument(new TextDocumentIdentifier(result.fileExportPath.toUri().toString()));
                        newParams.setPosition(new Position(result.hoverLocation.component1(), result.hoverLocation.component2()));
                        return this.languageServer.languageClientManager.hoverRequest(newParams).thenApply((h) -> {
                            // Process the result to try to format it
                            Pair<String, Integer> hoverInfo = getLanguageServerHoverRegex(result.fileExportPath);
                            Hover hov = new Hover();
                            if(hoverInfo != null){
                                // Use regex to get the type from the hover result
                                Pattern p = Pattern.compile(hoverInfo.component1());
                                String text = h.getContents().toString();
                                Matcher m = p.matcher(text);
                                if(m.find()){
                                    return getHoverObject(zipper, m.group(hoverInfo.component2()).replaceAll("\\\\n", "\n"), hostTree, hostTrees.size());
                                }
                            }
                            // Regex didn't worked, just return the hover result from the language server
                            hov.setContents(h.getContents());
                            setHoverRange(zipper, hov);
                            return hov;
                        });
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e);
        }
        return CompletableFuture.supplyAsync(() -> {return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, ""));});
    }

    /**
     * Return Hover object
     * @param nodeHovered Polyglot node that is hovered
     * @param type Type of variable hovered
     * @param hostTree The current hostTree of this analysis
     * @param numberHostTrees The number of hostTrees possible
     * @return Hover object
     */
    public Hover getHoverObject(PolyglotZipper nodeHovered, String type, PolyglotTreeHandler hostTree, int numberHostTrees){
        Hover hov = new Hover();
        String text = "```typescript\nPolyglot "+nodeHovered.getCode()+" : "+type+"\n```\n"+(numberHostTrees>1 ? "(With Host : "+PolyglotTreeHandler.getfilePathOfTreeHandler().get(hostTree).getFileName()+") + "+(numberHostTrees-1)+" more host(s)..." : "");
        hov.setContents(new MarkupContent(MarkupKind.MARKDOWN, text));
        setHoverRange(nodeHovered, hov);
        return hov;
    }

    /**
     * Set the range of the hover event depending on the node which is hovered
     * @param nodeHovered Polyglot node hovered
     * @param hover the hover object to edit
     */
    public void setHoverRange(PolyglotZipper nodeHovered, Hover hover){
        Range r = new Range();
        r.setStart(new Position(nodeHovered.getPosition().component1(), nodeHovered.getPosition().component2()));
        r.setEnd(new Position(nodeHovered.getPosition().component1(), nodeHovered.getPosition().component2() + nodeHovered.getCode().length()));
        hover.setRange(r);
    }

    /**
     * Get Hover Regex info depending on the file path
     * @param path path of file where the hover event is triggered
     * @return Pair <String : Regex pattern, int : group to catch>
     */
    public Pair<String, Integer> getLanguageServerHoverRegex(Path path){
        String pathS = path.toString();
        int index = pathS.lastIndexOf('.');
        if(index > 0){
            String extension = pathS.substring(index+1);
            String language = getLanguageFromExtension(extension);
            if(language.equals("none")) return null;
            PolyglotLanguageServerProperties.LanguageServerInfo info = this.languageServer.getLanguageInfo(language);
            if(info.hoverRegex == null || info.hoverRegex.equals("")) return null;
            return new Pair<>(info.hoverRegex, info.hoverRegexGroup);
        }
        return null;
    }

    /**
     * ##################################### NOT IMPLEMENTED LSP REQUEST ###############################################
     */

    /**
     * LSP codeAction Request Handler
     */
    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return TextDocumentService.super.codeAction(params);
    }

    /**
     * LSP resolveCodeAction Request Handler
     */
    @Override
    public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
        return TextDocumentService.super.resolveCodeAction(unresolved);
    }

    /**
     * LSP diagnostic Request Handler
     */
    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
        return TextDocumentService.super.diagnostic(params);
    }

    /**
     * ################################################## UTILS ########################################################
     */

    /**
     * Return programming language name from the file extension
     * @param extension file extension
     * @return programming language name of the file, "none" if not found
     */
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

}
