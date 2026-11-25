package chess.ui;

import chess.analysis.WinChance;
import chess.engine.Evaluation;
import chess.model.GameResult;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/** Vertical bar: the white part shows White's winning chances. */
public class EvalBar extends JPanel {
    //==================================================
    // Fields
    private double whitePercent = 50.0;
    private String label = "0.0";
    private boolean flipped;                 // true -> White is at the top (player is Black)
    private boolean showEvaluation = true;   // false -> draw an empty gray bar
    //==================================================

    public EvalBar() {
        setPreferredSize(new Dimension(44, 640));
    }

    //==================================================
    // State
    public void setEvaluation(Evaluation ev) {
        whitePercent = WinChance.whitePercent(ev);

        if(ev.mate()) label = "M" + Math.abs(ev.value());
        else label = String.format(Locale.US, "%+.1f", ev.value() / 100.0);

        repaint();
    }

    public void setResult(GameResult r) {
        switch (r) {
            case WHITE_WINS:
                whitePercent = 100.0;
                label = "1-0";
                break;
            case BLACK_WINS:
                whitePercent = 0.0;
                label = "0-1";
                break;
            case DRAW:
                whitePercent = 50.0;
                label = "½";
                break;
        }

        repaint();
    }

    public void reset() {
        whitePercent = 50.0;
        label = "0.0";

        repaint();
    }

    /** Show/hide the evaluation. The data is kept, it is just not drawn. */
    public void setShowEvaluation(boolean show) {
        showEvaluation = show;
        repaint();
    }

    public void setFlipped(boolean b) {
        flipped = b;
        repaint();
    }
    //==================================================

    //==================================================
    // Graphics
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();

        if (!showEvaluation) {               // analysis is off: neutral bar
            g2.setColor(Color.GRAY);
            g2.fillRect(0, 0, w, h);
            return;
        }
        int whiteH = (int) Math.round(h * whitePercent / 100.0);
        int whiteY = flipped ? 0 : h - whiteH;

        g2.setColor(new Color(50, 50, 50));      // black part
        g2.fillRect(0, 0, w, h);
        g2.setColor(Color.WHITE);                        // white part
        g2.fillRect(0, whiteY, w, whiteH);

        // Label on the end of dominant part
        boolean whiteLeads = whitePercent >= 50;
        boolean atBottom = whiteLeads != flipped;

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();

        int x = (w - fm.stringWidth(label)) / 2;
        int y = atBottom ? h - 6 : fm.getAscent() + 6;
        g2.setColor(whiteLeads ? Color.DARK_GRAY : Color.LIGHT_GRAY);   // contrast
        g2.drawString(label, x, y);
    }
    //==================================================
}
