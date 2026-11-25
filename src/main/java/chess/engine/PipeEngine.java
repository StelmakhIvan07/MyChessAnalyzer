package chess.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs stockfish.exe as a separate process and talks to it with text lines (UCI protocol).
 * Commands go to the process input; every output line is sent to all listeners.
 */
public class PipeEngine implements AutoCloseable {
    //==================================================
    // Fields
    private final Process stockfishProcess;
    private final BufferedWriter writer;
    // thread-safe list: the reader thread iterates it while others add/remove
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    //==================================================

    public PipeEngine(Path exe) throws IOException {
        if(!Files.isRegularFile(exe))
            throw new IOException("Рушій не знайдено: " + exe.toAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(exe.toString());
        pb.redirectErrorStream(true);
        stockfishProcess = pb.start();

        writer = new BufferedWriter(
                new OutputStreamWriter(stockfishProcess.getOutputStream(), StandardCharsets.UTF_8)
        );

        // daemon thread: does not keep the program alive after the window is closed
        Thread reader = new Thread(this::readLoop, "uci-reader");
        reader.setDaemon(true);
        reader.start();

    }

    //==================================================
    // Lifecycle
    public CompletableFuture<Void> init() {
        return sendAndAwait("uci", "uciok")
                .thenCompose(x -> sendAndAwait("isready", "readyok"))
                .orTimeout(5, TimeUnit.SECONDS)
                .thenAccept(x -> { });
    }

    public boolean isAlive() { return stockfishProcess.isAlive(); }

    /** Asks the engine to quit; kills the process if it does not exit in 2 seconds. */
    @Override
    public void close(){
        try { send("quit"); } catch (RuntimeException ignored) { }

        try {
            if (!stockfishProcess.waitFor(2, TimeUnit.SECONDS))
                stockfishProcess.destroyForcibly();
        } catch (InterruptedException e) {
            stockfishProcess.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
    //==================================================

    //==================================================
    // Sending commands
    public synchronized void send(String cmd) {
        try {
            writer.write(cmd);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    /**
     * Sends a command and returns a future that completes with the first
     * output line starting with prefix (e.g. "isready" -> "readyok").
     */
    public CompletableFuture<String> sendAndAwait(String cmd, String prefix) {
        CompletableFuture<String> f = new CompletableFuture<>();

        // anonymous class (not a lambda) so the listener can remove itself with "this"
        Consumer<String> l = new Consumer<>() {
            @Override
            public void accept(String line) {
                if (line.startsWith(prefix)) {
                    removeListener(this);
                    f.complete(line);
                }
            }
        };

        addListener(l);
        try {
            send(cmd);
        } catch (RuntimeException e) {
            removeListener(l);             // command was not sent: do not leave the listener hanging
            f.completeExceptionally(e);    // and report that no answer will come
        }
        return f;
    }
    //==================================================

    //==================================================
    // Reading output
    public void addListener(Consumer<String> l) { listeners.add(l); }
    public void removeListener(Consumer<String> l) { listeners.remove(l); }

    /** Runs on the "uci-reader" thread until the process ends. */
    private void readLoop() {
        try(BufferedReader reader = new BufferedReader(
          new InputStreamReader(stockfishProcess.getInputStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while((line = reader.readLine()) != null) {
                for(Consumer<String> l: listeners) {
                    try {
                        l.accept(line);
                    } catch(Exception e) {
                        e.printStackTrace();   // one broken listener must not stop the loop
                    }
                }
            }
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }
    //==================================================
}
