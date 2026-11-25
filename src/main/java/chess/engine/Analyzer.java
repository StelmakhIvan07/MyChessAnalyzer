package chess.engine;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Infinite analysis with the second Stockfish.
 * Results are stored per position (FEN), the deepest one wins.
 *
 * Switching positions: Stockfish must answer "bestmove" to "stop" before a new search,
 * so analyze() only remembers the wanted position and sends "stop";
 * the new search starts in onBestMove().
 */
public class Analyzer {
    //==================================================
    // Fields
    private final PipeEngine engine;
    private final Map<String, Evaluation> results = new ConcurrentHashMap<>();
    private volatile Evaluation latest;

    private boolean searching;             // "go" sent, "bestmove" not received yet
    private boolean stopSent;
    private volatile String searchedFen;   // position being searched now
    private String wantedFen;              // position we want to see (null: analysis is off)
    //==================================================

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

    //==================================================
    // Results
    public Evaluation latest() { return latest; }

    public Evaluation get(String fen) {
        return fen == null ? null : results.get(fen);
    }

    public void clear() { results.clear(); latest = null; }
    //==================================================

    //==================================================
    // Control (synchronized: called from Swing, onBestMove from the reader thread)
    public synchronized void analyze(String fen) {
        wantedFen = fen;
        if(!searching) { startSearch(); return; }
        if(!stopSent && fen.equals(searchedFen)) return;   // already searching this position
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
    //==================================================

    //==================================================
    // Engine output (reader thread)
    private void onLine(String line) {
        if (line.startsWith("info ")) {
            Evaluation ev = Evaluation.parse(line, searchedFen);
            if (ev == null) return;
            latest = ev;
            // keep the deeper evaluation of the same position
            results.merge(ev.fen(), ev, (old, nw) -> nw.depth() >= old.depth() ? nw : old);
        } else if (line.startsWith("bestmove")) {
            onBestMove();
        }
    }

    /** Search finished; if it was stopped to switch position, start the new one. */
    private synchronized void onBestMove() {
        searching = false;
        if (stopSent && wantedFen != null) startSearch();
        else stopSent = false;
    }
    //==================================================
}
