package chess.engine;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class Analyzer {
    private final PipeEngine engine;
    private final Map<String, Evaluation> results = new ConcurrentHashMap<>();
    private volatile Evaluation latest;

    private boolean searching;             // go надіслано, bestmove ще не було
    private boolean stopSent;
    private volatile String searchedFen;   // що шукаємо зараз
    private String wantedFen;              // що хочемо бачити (null: аналіз вимкнено)

    public Analyzer(PipeEngine engine) {
        this.engine = engine;
        engine.addListener(this::onLine);
    }

    public CompletableFuture<Void> init() {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        return engine.init()
                .thenRun(() -> {
                    engine.send("setoption name Threads value " + threads);
                    engine.send("setoption name Hash value 256");
                })
                .thenCompose(v -> engine.sendAndAwait("isready", "readyok"))
                .thenAccept(x -> { });
    }

    public Evaluation latest() { return latest; }

    public Evaluation get(String fen) {
        return fen == null ? null : results.get(fen);
    }

    public void clear() { results.clear(); latest = null; }

    public synchronized void analyze(String fen) {
        wantedFen = fen;
        if(!searching) { startSearch(); return; }
        if(!stopSent && fen.equals(searchedFen)) return;
        requestStop();
    }

    public synchronized void stopAnalysis() {
        wantedFen = null;
        if(searching) requestStop();
    }

    private void requestStop() {
        if(!stopSent) {
            stopSent = true;
            engine.send("stop");
        }
    }

    private void startSearch() {
        searchedFen = wantedFen;
        searching = true;
        stopSent = false;
        engine.send("position fen " + searchedFen);
        engine.send("go infinite");
    }

    private void onLine(String line) {
        if (line.startsWith("info ")) {
            Evaluation ev = Evaluation.parse(line, searchedFen);
            if (ev == null) return;
            latest = ev;
            results.merge(ev.fen(), ev, (old, nw) -> nw.depth() >= old.depth() ? nw : old);
        } else if (line.startsWith("bestmove")) {
            onBestMove();
        }
    }

    private synchronized void onBestMove() {
        searching = false;
        if (stopSent && wantedFen != null) startSearch();
        else stopSent = false;
    }
}
