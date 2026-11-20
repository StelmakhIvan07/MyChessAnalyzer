package chess.engine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Bot{
    private final PipeEngine engine;
    private static final int ELO_MIN = 1320;
    private static final int ELO_MAX = 3190;

    private int elo = 1500;
    private int moveTimeMs = 500;

    private volatile CompletableFuture<String> pending;

    public Bot(PipeEngine engine) {
        this.engine = engine;
    }

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

    public CompletableFuture<String> chooseMove(List<String> uciMoves) {
        if (pending != null && !pending.isDone())
            throw new IllegalStateException("Бот уже думає");

        String position = uciMoves.isEmpty()
                ? "position startpos"
                : "position startpos moves " + String.join(" ", uciMoves);

        engine.send(position);

        CompletableFuture<String> f = engine.sendAndAwait("go movetime " + moveTimeMs, "bestmove")
                .orTimeout(moveTimeMs + 5000L, TimeUnit.MILLISECONDS)
                .thenApply(line -> {
                    String move = line.split(" ")[1];
                    return move.equals("(none)") ? null: move;
                });

        pending = f;
        return f;
    }

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


    public void setMoveTimeMs(int ms) {
        this.moveTimeMs = ms;
    }
}
