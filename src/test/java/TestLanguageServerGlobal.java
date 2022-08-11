import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestLanguageServerGlobal implements LanguageClient {

    ServerSocket socket;
    RemoteEndpoint remoteEndpoint;
    Object resultRequest = null;
    private CountDownLatch lock = new CountDownLatch(1);

    @BeforeAll
    public void initializationTest() throws IOException, InterruptedException {
        resultRequest = null;
        TestLanguageServerGlobal client = new TestLanguageServerGlobal();
        socket = new ServerSocket( 2088);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PolyglotLanguageServerLauncher.main(null);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();

        Socket ls = socket.accept();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(client, ls.getInputStream(), ls.getOutputStream());

        InitializeParams params = new InitializeParams();
        params.setClientInfo(new ClientInfo("Client test", "v1"));
        ClientCapabilities capa = new ClientCapabilities();
        params.setCapabilities(capa);

        File file = new File("src/test/resources/");
        params.setRootUri(Paths.get(file.getAbsolutePath()).toUri().toString());

        launcher.getRemoteProxy().initialize(params).thenApply(k -> {
            resultRequest = k;
            lock.countDown();
            launcher.getRemoteProxy().initialized(new InitializedParams());
            return k;
        });

        remoteEndpoint = launcher.getRemoteEndpoint();
        launcher.startListening();

        lock.await(2000, TimeUnit.MILLISECONDS);
    }

    @Test
    @Order(1)
    public void initializationResult(){
        assertNotNull(resultRequest);
    }

    @Test
    @Order(2)
    public void openAndHoverTest() throws IOException, InterruptedException {
        resultRequest = null;
        lock = new CountDownLatch(1);
        File file = new File("src/test/resources/maintest.py");
        Path path = Path.of(file.getAbsolutePath());
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
        TextDocumentItem tdi = new TextDocumentItem();
        tdi.setVersion(1);
        tdi.setLanguageId("python");
        tdi.setUri(path.toUri().toString());
        tdi.setText(Files.readString(path));
        params.setTextDocument(tdi);
        remoteEndpoint.notify("textDocument/didOpen", params);
        lock.await(5000, TimeUnit.MILLISECONDS);
        lock = new CountDownLatch(1);

        HoverParams hover = new HoverParams();
        hover.setTextDocument(new TextDocumentIdentifier(path.toUri().toString()));
        hover.setPosition(new Position(5,0));

        remoteEndpoint.request("textDocument/hover", hover).thenApply(v -> {
            resultRequest = v;
            lock.countDown();
            return v;
        });

        lock.await(2000, TimeUnit.MILLISECONDS);

        assertNotNull(resultRequest);
        assertEquals(((Hover)resultRequest).getContents().getRight().getValue(), "```typescript\nPolyglot var_var_arr : number[]\n```\n");
    }

    @AfterAll
    public void end(){
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * USELESS METHODS
     */
    @Override
    public void telemetryEvent(Object object) {

    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {

    }

    @Override
    public void showMessage(MessageParams messageParams) {

    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return null;
    }

    @Override
    public void logMessage(MessageParams message) {

    }
}
