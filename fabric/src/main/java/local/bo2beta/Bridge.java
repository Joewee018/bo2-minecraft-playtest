package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded loopback worker; never blocks Minecraft's tick or render thread. */
public final class Bridge implements AutoCloseable {
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"BO2 beta bridge");t.setDaemon(true);return t;});
    public CompletableFuture<JsonObject> request(JsonObject request) {
        var result=new CompletableFuture<JsonObject>();
        try {worker.execute(()->{try {result.complete(exchange(request));}catch(Exception e){result.completeExceptionally(e);}});}
        catch(RejectedExecutionException e){result.completeExceptionally(e);}return result;
    }
    private JsonObject exchange(JsonObject request)throws IOException {
        String token=System.getenv("BO2_BETA_TOKEN");if(token==null)token=System.getProperty("bo2beta.token");
        if(token==null || token.length()<32)throw new IOException("Start the beta with its companion launcher");
        request.addProperty("token",token);request.addProperty("protocol",1);
        try(var socket=new Socket()) {
            String value=System.getenv("BO2_BETA_PORT");if(value==null)value=System.getProperty("bo2beta.port");int port=value==null?28762:Integer.parseInt(value);
            if(port<1 || port>65535)throw new IOException("Invalid local bridge port");
            socket.connect(new InetSocketAddress("127.0.0.1",port),1000);socket.setSoTimeout(1500);
            var out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            out.write(request.toString());out.write('\n');out.flush();
            var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            var line=new StringBuilder();int c;
            while((c=in.read())!=-1 && c!='\n') {if(line.length()>=8192)throw new IOException("Oversized bridge response");line.append((char)c);}
            if(c!='\n')throw new IOException("Truncated bridge response");
            return JsonParser.parseString(line.toString()).getAsJsonObject();
        }
    }
    @Override public void close(){worker.shutdownNow();}
}
