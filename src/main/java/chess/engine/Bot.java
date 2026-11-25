package chess.engine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** The opponent: first Stockfish with limited strength (UCI_Elo). */
public class Bot{
    //==================================================
    // Fields
    private final PipeEngine engine;
    public static final int ELO_MIN = 1320;   // public: used by the difficulty buttons
    public static final int ELO_MAX = 3190;   // limits of Stockfish's UCI_Elo

    private int elo = 1500;
    private int moveTimeMs = 500;

    private volatile CompletableFuture<String> pending;   // move the bot is thinking about now
    //==================================================

    public Bot(PipeEngine engine) {
        this.engine = engine;
    }

    //==================================================
    // Setup
    public CompletableFuture<Void> init() {
        return engine.init()
                .thenRun(() -> {
                    engine.send("setoption name Threads value 1");
                    engine.send("setoption name Hash value 16");
                    engine.send("setoption name UCI_LimitStrength value true");
                    engine.send("setoption name UCI_Elo value " + elo);
                })
                .thenCompose(v -> engine.sendAndAwait("isready", "readyok"))
                .thenAccept(x -> { });
    }

    public void setElo(int eloValue) {
        this.elo = Math.clamp(eloValue, ELO_MIN, ELO_MAX);
        engine.send("setoption name UCI_elo value " + elo);
    }

    public void setMoveTimeMs(int ms) {
        this.moveTimeMs = ms;
    }
    //==================================================

    //==================================================
    // Playing

    /**
     * Sends the whole game and asks for a move.
     * The future completes with a UCI move ("e7e5") or null if there is no move.
     */
    public CompletableFuture<String> chooseMove(List<String> uciMoves) {
        if (pending != null && !pending.isDone())
            throw new IllegalStateException("Бот уже думає");

        String position = uciMoves.isEmpty()
                ? "position startpos"
                : "position startpos moves " + String.join(" ", uciMoves);

        engine.send(position);

        // answer looks like "bestmove e7e5 ponder g1f3"
        CompletableFuture<String> f = engine.sendAndAwait("go movetime " + moveTimeMs, "bestmove")
                .orTimeout(moveTimeMs + 5000L, TimeUnit.MILLISECONDS)
                .thenApply(line -> {
                    String move = line.split(" ")[1];
                    return move.equals("(none)") ? null: move;
                });

        pending = f;
        return f;
    }

    /** Stops thinking; the future completes when the engine has really stopped. */
    public CompletableFuture<Void> cancel() {
        CompletableFuture<String> p = pending;
        if (p == null || p.isDone()) return CompletableFuture.completedFuture(null);
        engine.send("stop");
        return p.handle((r, e) -> null);
    }

    public CompletableFuture<Void> newGame() {
        return cancel()
                .thenRun(() -> engine.send("ucinewgame"))
                .thenCompose(v -> engine.sendAndAwait("isready", "readyok"))
                .thenAccept(x -> { });
    }
    //==================================================
}
