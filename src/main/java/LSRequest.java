import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class LSRequest {
    public Object params; // Parameter of the request
    public CompletableFuture<Object> response; // Completable future returned by the request
    public Function function; // Function of the request

    public LSRequest(Object params, CompletableFuture<Object> response, Function function) {
        this.params = params;
        this.response = response;
        this.function = function;
    }
}
