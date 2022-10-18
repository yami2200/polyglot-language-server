import com.example.polyglotast.PolyglotTreeHandler;
import com.example.polyglotast.PolyglotVariableSpotter;
import com.example.polyglotast.utils.ExportData;
import com.example.polyglotast.utils.ImportData;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class PolyglotLanguageServerBenchmarkLauncher implements LanguageClient {

    static ServerSocket socket;
    static RemoteEndpoint remoteEndpoint;
    static Object resultRequest = null;
    static CountDownLatch lock = new CountDownLatch(1);
    static PolyglotVariableSpotter polyglotVariableSpotter = new PolyglotVariableSpotter();
    static int current_it = 0;
    static ArrayList<ImportData> imports = new ArrayList<>();

    // to edit
    static int iteration = 250;
    static String projectRoot = "/home/yami/Polyglot Language Server/benchmark-file-maker/src/main/resources/";
    static String hostPath = "/home/yami/Polyglot Language Server/benchmark-file-maker/src/main/resources/100ExportsImports3/2files_host_1,6k.py";
    static String hostLanguage = "python";

    public static void main(String[] args) throws IOException, InterruptedException {
        resultRequest = null;
        PolyglotLanguageServerBenchmarkLauncher client = new PolyglotLanguageServerBenchmarkLauncher();
        socket = new ServerSocket( 2088);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PolyglotLanguageServerLauncher.main(null);
                } catch (InterruptedException | IOException | ExecutionException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();

        Socket ls = socket.accept();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client, ls.getInputStream(), ls.getOutputStream());

        InitializeParams params_init = new InitializeParams();
        params_init.setClientInfo(new ClientInfo("Client test", "v1"));
        ClientCapabilities capa = new ClientCapabilities();
        params_init.setCapabilities(capa);

        File file_root = new File(projectRoot);
        params_init.setRootUri(Paths.get(file_root.getAbsolutePath()).toUri().toString());

        launcher.getRemoteProxy().initialize(params_init).thenApply(k -> {
            resultRequest = k;
            lock.countDown();
            launcher.getRemoteProxy().initialized(new InitializedParams());
            return k;
        });

        remoteEndpoint = launcher.getRemoteEndpoint();
        launcher.startListening();

        lock.await(3000, TimeUnit.MILLISECONDS);

        if(resultRequest == null) throw new RuntimeException("Initialization failed");
        System.out.println(resultRequest);

        lock = new CountDownLatch(1);

        File file = new File(hostPath);
        Path path = Path.of(file.getAbsolutePath());
        PolyglotTreeHandler polyglotTreeHandler = new PolyglotTreeHandler(Paths.get(file.getAbsolutePath()), hostLanguage);
        polyglotTreeHandler.apply(polyglotVariableSpotter);
        polyglotTreeHandler.clearTreeHandlerPathAndInstance();

        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        TextDocumentItem tdi = new TextDocumentItem();
        tdi.setVersion(1);
        tdi.setLanguageId(hostLanguage);
        tdi.setUri(path.toUri().toString());
        tdi.setText(Files.readString(path));
        params.setTextDocument(tdi);
        remoteEndpoint.notify("textDocument/didOpen", params);

        lock.await(5000, TimeUnit.MILLISECONDS);

        for (String s : polyglotVariableSpotter.getImports().keySet()) {
            for (PolyglotTreeHandler treeHandler : polyglotVariableSpotter.getImports().get(s).keySet()) {
                for (ImportData importData : polyglotVariableSpotter.getImports().get(s).get(treeHandler)) {
                    imports.add(importData);
                }
            }
        }

        //didOpenRequest();
        //completion();
        //rename();
        //hover();
    }

    /** code to insert around methods **/
    /*
    long start = System.currentTimeMillis();

    // code to measure

    long finish = System.currentTimeMillis();
    long timeElapsed = finish - start;
    String csvPath = "...";
    try{
        // write to csv
    }catch (Exception e){
        System.err.println(e.getMessage());
    }
    */
    /** ############################# **/

    public static void didOpenRequest() throws InterruptedException, IOException {
        lock = new CountDownLatch(1);

        File file = new File(hostPath);
        Path path = Path.of(file.getAbsolutePath());
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        TextDocumentItem tdi = new TextDocumentItem();
        tdi.setVersion(1);
        tdi.setLanguageId(hostLanguage);
        tdi.setUri(path.toUri().toString());
        tdi.setText(Files.readString(path));
        params.setTextDocument(tdi);
        remoteEndpoint.notify("textDocument/didOpen", params);

        lock.await(7000, TimeUnit.MILLISECONDS);

        System.out.println("Iteration " + current_it + " done");
        current_it++;
        if(current_it <= iteration) didOpenRequest();
    }

    public static void completion() throws InterruptedException, IOException {
        lock = new CountDownLatch(1);

        File file;
        Position pos = new Position();
        pos.setLine(3);
        pos.setCharacter(0);
        file = new File(((Path)PolyglotTreeHandler.getfilePathToTreeHandler().keySet().toArray()[new Random().nextInt(PolyglotTreeHandler.getfilePathToTreeHandler().keySet().size())]).toString());
        Path path = Path.of(file.getAbsolutePath());
        CompletionParams params = new CompletionParams();
        TextDocumentIdentifier tdi = new TextDocumentIdentifier();
        tdi.setUri(path.toUri().toString());
        params.setTextDocument(tdi);
        params.setPosition(pos);
        remoteEndpoint.request("textDocument/completion", params).thenApply(k -> {
            lock.countDown();
            return k;
        });

        System.out.println("Iteration " + current_it + " done");
        current_it++;
        if(current_it <= iteration) completion();
    }

    public static void rename()throws InterruptedException, IOException {
        lock = new CountDownLatch(1);

        File file;
        Position pos = new Position();
        if(new Random().nextBoolean()){
            HashMap<PolyglotTreeHandler, ArrayList<ExportData>> map = polyglotVariableSpotter.getExports().get((String)polyglotVariableSpotter.getExports().keySet().toArray()[new Random().nextInt(polyglotVariableSpotter.getExports().keySet().size())]);
            ArrayList<ExportData> list = map.get((PolyglotTreeHandler)map.keySet().toArray()[new Random().nextInt(map.keySet().size())]);
            ExportData exportData = list.get(new Random().nextInt(list.size()));
            file = new File(exportData.getFilePath().toString());
            pos.setLine(exportData.getVar_name_position().component1());
            pos.setCharacter(exportData.getVar_name_position().component2());
        } else {
            HashMap<PolyglotTreeHandler, ArrayList<ImportData>> map = polyglotVariableSpotter.getImports().get((String)polyglotVariableSpotter.getImports().keySet().toArray()[new Random().nextInt(polyglotVariableSpotter.getImports().keySet().size())]);
            ArrayList<ImportData> list = map.get((PolyglotTreeHandler)map.keySet().toArray()[new Random().nextInt(map.keySet().size())]);
            ImportData importData = list.get(new Random().nextInt(list.size()));
            file = new File(importData.getFilePath().toString());
            pos.setLine(importData.getVar_name_position().component1());
            pos.setCharacter(importData.getVar_name_position().component2());
        }
        RenameParams params = new RenameParams();
        TextDocumentIdentifier tdi = new TextDocumentIdentifier();
        tdi.setUri(file.toPath().toUri().toString());
        params.setTextDocument(tdi);
        params.setPosition(pos);
        params.setNewName("test"+new Random().nextInt(10000));

        remoteEndpoint.request("textDocument/rename", params).thenApply(k -> {
            System.out.println(k);
            if(k==null) current_it--;
            lock.countDown();
            return k;
        });

        System.out.println("Iteration " + current_it + " done");
        current_it++;
        if(current_it <= iteration) rename();
    }

    public static void hover() throws InterruptedException, IOException {
        lock = new CountDownLatch(1);

        File file;
        Position pos = new Position();
            int index = new Random().nextInt(imports.size());
            ImportData importData = imports.get(index);
            while (importData.storageVarPosition == null){
                imports.remove(index);
                index = new Random().nextInt(imports.size());
                importData = imports.get(index);
            }
            file = new File(importData.getFilePath().toString());
            pos.setLine(importData.storageVarPosition.component1());
            pos.setCharacter(importData.storageVarPosition.component2());

            HoverParams params = new HoverParams();
            TextDocumentIdentifier tdi = new TextDocumentIdentifier();
            tdi.setUri(file.toPath().toUri().toString());
            params.setTextDocument(tdi);
            params.setPosition(pos);

            final int finalIndex = index;

            remoteEndpoint.request("textDocument/hover", params).thenApply(k -> {
                if(k==null) {
                    current_it--;
                    imports.remove(finalIndex);
                }
                lock.countDown();
                return k;
            });


        lock.await(7000, TimeUnit.MILLISECONDS);

        current_it++;
        System.out.println("Iteration " + current_it + " done");
        if(current_it <= iteration) hover();
    }

    @Override
    public void telemetryEvent(Object o) {

    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {
        System.out.println("Diagnostics received");
        System.out.println(publishDiagnosticsParams.getDiagnostics().size());
    }

    @Override
    public void showMessage(MessageParams messageParams) {

    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams) {
        return null;
    }

    @Override
    public void logMessage(MessageParams messageParams) {

    }
}
