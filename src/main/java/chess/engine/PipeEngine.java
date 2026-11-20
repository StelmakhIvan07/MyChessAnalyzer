package chess.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PipeEngine implements AutoCloseable {
    private final Process stockfishProcess;
    private final BufferedWriter writer;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public PipeEngine(Path exe) throws IOException {
        if(!Files.isRegularFile(exe))
            throw new IOException("Рушій не знайдено: " + exe.toAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(exe.toString());
        pb.redirectErrorStream(true);
        stockfishProcess = pb.start();

        writer = new BufferedWriter(
                new OutputStreamWriter(stockfishProcess.getOutputStream(), StandardCharsets.UTF_8)
        );

        Thread reader = new Thread(this::readLoop, "uci-reader");
        reader.setDaemon(true);
        reader.start();

    }

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
                        e.printStackTrace();
                    }
                }
            }
        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public synchronized void send(String cmd) {
        try {
            writer.write(cmd);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public void addListener(Consumer<String> l) { listeners.add(l); }
    public void removeListener(Consumer<String> l) { listeners.remove(l); }

    public CompletableFuture<String> sendAndAwait(String cmd, String prefix) {
        CompletableFuture<String> f = new CompletableFuture<>();

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
            removeListener(l);             // команда не пішла: не лишаємо слухача висіти
            f.completeExceptionally(e);    // і повідомляємо, що відповіді не буде
        }
        return f;
    }

    public CompletableFuture<Void> init() {
        return sendAndAwait("uci", "uciok")
                .thenCompose(x -> sendAndAwait("isready", "readyok"))
                .orTimeout(5, TimeUnit.SECONDS)
                .thenAccept(x -> { });
    }

    public boolean isAlive() { return stockfishProcess.isAlive(); }

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
}
