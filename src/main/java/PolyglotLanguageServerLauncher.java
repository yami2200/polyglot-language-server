import com.example.polyglotast.PolyglotDUBuilder;
import com.example.polyglotast.PolyglotTreeHandler;
import com.example.polyglotast.PolyglotTreePrinter;
import kotlin.Pair;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PolyglotLanguageServerLauncher {

    public static void main(String[] args) throws InterruptedException, ExecutionException, URISyntaxException, IOException {
        try {
            Socket clientSocket = new Socket("127.0.0.1", 2088);
            startServer(clientSocket.getInputStream(), clientSocket.getOutputStream());
        } catch (IOException | InterruptedException | ExecutionException e) {
            System.err.println(e);
        }

        //startServer(System.in, System.out);
    }

    /**
     * Starts the language server given the input and output streams to read and write messages.
     * @param in  input stream.
     * @param out output stream.
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public static void startServer(InputStream in, OutputStream out) throws InterruptedException, ExecutionException, URISyntaxException, IOException {
        PolyglotLanguageServer server = new PolyglotLanguageServer();
        Launcher<LanguageClient> launcher = Launcher.createLauncher(server, LanguageClient.class, in, out);
        LanguageClient client = launcher.getRemoteProxy();
        server.connect(client);
        Future<?> startListening = launcher.startListening();
        startListening.get();
    }

}
