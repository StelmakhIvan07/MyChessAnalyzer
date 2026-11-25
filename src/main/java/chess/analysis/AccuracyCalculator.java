package chess.analysis;

import java.util.List;

import static java.lang.Math.exp;

/** Accuracy in percent from the win-chance loss of each move (100 = perfect). */
public class AccuracyCalculator {
    /** Exponential curve: loss 0 -> 100%, the bigger the loss, the lower the accuracy. */
    public static double moveAccuracy(double loss) {
        double a = 103.1668 * exp(-0.04354 * loss) - 3.1669;
        return Math.clamp(a, 0, 100);
    }

    public static double accuracy(List<Double> losses) {
        if (losses.isEmpty()) return 100.0;
        double sum = 0;
        for (double loss : losses) sum += moveAccuracy(loss);
        return sum / losses.size();
    }
}
