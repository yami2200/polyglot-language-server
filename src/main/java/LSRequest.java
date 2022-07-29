import java.util.concurrent.CompletableFuture;

public class LSRequest {
    public String id;
    public Object params;
    public CompletableFuture<Object> response;

    public LSRequest(String id, Object params, CompletableFuture<Object> response) {
        this.id = id;
        this.params = params;
        this.response = response;
    }
}
