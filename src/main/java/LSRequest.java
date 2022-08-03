import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class LSRequest {
    public String id;
    public Object params;
    public CompletableFuture<Object> response;
    public Function function;

    public LSRequest(String id, Object params, CompletableFuture<Object> response, Function function) {
        this.id = id;
        this.params = params;
        this.response = response;
        this.function = function;
    }
}
