package chess.analysis;

import java.util.List;

import static java.lang.Math.exp;

public class AccuracyCalculator {
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
