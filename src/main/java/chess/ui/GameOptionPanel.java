package chess.ui;

import chess.engine.Bot;
import com.github.bhlangonijr.chesslib.Side;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Left panel: side, mode, bot difficulty, "Start game" button and "Show analysis" checkbox.
 * It knows nothing about the game: the choice is passed out through the two callbacks.
 */
public class GameOptionPanel extends JPanel {
    public interface StartHandler {
        void start(Side side, boolean vsBot, int elo);
    }

    //==================================================
    // Fields

    // Side choose buttons
    private final JToggleButton whiteButton  = toggle("White");
    private final JToggleButton blackButton  = toggle("Black");
    private final JToggleButton randomButton = toggle("Random");

    // Mode choose buttons
    private final JToggleButton botButton  = toggle("Play with Bot");
    private final JToggleButton soloButton = toggle("Solo game");

    // Bot difficulty
    private final JToggleButton easyButton = toggle("Easy");
    private final JToggleButton mediumButton = toggle("Medium");
    private final JToggleButton hardButton = toggle("Hard");

    private static final int MEDIUM_ELO = 2200;

    // Wrapper of label "Difficulty" + difficulty row (hidden in solo game)
    private JPanel difficultyBlock;

    private final JCheckBox showAnalysisBox = new JCheckBox("Show analysis", true);
    //==================================================

    public GameOptionPanel(StartHandler startHandler, Consumer<Boolean> onShowAnalysis) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        //-------------------------------------
        // Button groups (only one button of a group can be selected)
        ButtonGroup sideGroup = new ButtonGroup();
        sideGroup.add(whiteButton);
        sideGroup.add(blackButton);
        sideGroup.add(randomButton);
        randomButton.setSelected(true);           // default

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(botButton);
        modeGroup.add(soloButton);
        botButton.setSelected(true);

        // Only one difficulty can be selected
        ButtonGroup diffGroup = new ButtonGroup();
        diffGroup.add(easyButton);
        diffGroup.add(mediumButton);
        diffGroup.add(hardButton);
        mediumButton.setSelected(true);

        //-------------------------------------
        // Rows
        // "White | Black | Random"
        JPanel sideRow = new JPanel(new GridLayout(1, 3, 4, 0));
        sideRow.add(whiteButton);
        sideRow.add(blackButton);
        sideRow.add(randomButton);

        // "Play with Bot | Solo game"
        JPanel modeRow = new JPanel(new GridLayout(1, 2, 4, 0));
        modeRow.add(botButton);
        modeRow.add(soloButton);

        // "Easy | Medium | Hard"
        JPanel diffRow = new JPanel(new GridLayout(1, 3, 4, 0));
        diffRow.add(easyButton);
        diffRow.add(mediumButton);
        diffRow.add(hardButton);

        //-------------------------------------
        // Actions
        JButton startButton = new JButton("Start game");
        startButton.setFocusable(false);
        startButton.addActionListener(e ->
                startHandler.start(chosenSide(), botButton.isSelected(), chosenElo()));

        // Label + row in one block, so we can hide both with one call
        difficultyBlock = new JPanel(new BorderLayout());
        difficultyBlock.add(new JLabel("Difficulty"), BorderLayout.NORTH);
        difficultyBlock.add(diffRow, BorderLayout.CENTER);

        // Any click on a mode button -> show/hide the difficulty block
        botButton.addActionListener(e -> updateDifficultyVisibility());
        soloButton.addActionListener(e -> updateDifficultyVisibility());

        showAnalysisBox.setFocusable(false);
        showAnalysisBox.addActionListener(e -> onShowAnalysis.accept(showAnalysisBox.isSelected()));

        //-------------------------------------
        // Layout (top to bottom)
        add(row(new JLabel("Side")));
        add(row(sideRow));
        add(Box.createVerticalStrut(10));
        add(row(new JLabel("Mode")));
        add(row(modeRow));
        add(row(difficultyBlock));
        add(Box.createVerticalStrut(10));
        add(row(startButton));
        add(Box.createVerticalStrut(20));
        add(row(showAnalysisBox));
        add(Box.createVerticalGlue());   // pushes everything up
    }

    //==================================================
    // Reading the choice
    private Side chosenSide() {
        if (whiteButton.isSelected()) return Side.WHITE;
        if (blackButton.isSelected()) return Side.BLACK;
        return Math.random() < 0.5 ? Side.WHITE : Side.BLACK;
    }

    private int chosenElo() {
        if (easyButton.isSelected()) return Bot.ELO_MIN;
        if (hardButton.isSelected()) return Bot.ELO_MAX;
        return MEDIUM_ELO;
    }

    public boolean isShowAnalysis() { return showAnalysisBox.isSelected(); }

    /** Difficulty is visible only when "Play with Bot" is selected. */
    private void updateDifficultyVisibility() {
        // row() wrapped difficultyBlock in another panel -> hide that wrapper
        difficultyBlock.getParent().setVisible(botButton.isSelected());
        revalidate();   // hidden component takes no space
        repaint();
    }
    //==================================================

    //==================================================
    // Helpers
    private static JToggleButton toggle(String text) {
        JToggleButton b = new JToggleButton(text);
        b.setFocusable(false);   // keep keyboard shortcuts (arrows) working
        return b;
    }

    /** Wraps a component so BoxLayout gives every row full width and its natural height. */
    private static JPanel row(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }
    //==================================================
}
