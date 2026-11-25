package chess.ui;

import chess.analysis.MoveClass;
import chess.analysis.MoveReview;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public class MoveListPanel extends JPanel {
    private final DefaultTableModel tableModel =
            new DefaultTableModel(new Object[]{"#", "White", "Black"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable table = new JTable(tableModel);
    private final JLabel whiteAcc = new JLabel("White: —", SwingConstants.CENTER);
    private final JLabel blackAcc = new JLabel("Black: —", SwingConstants.CENTER);


    private List<MoveReview> reviews = List.of();
    private int plyCount = 0;
    private int current = 0;
    private IntConsumer onSelect = i -> { };

    public MoveListPanel() {
        super(new BorderLayout());

        JPanel accHeader = new JPanel(new GridLayout(1, 2));
        accHeader.add(whiteAcc);
        accHeader.add(blackAcc);

        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        table.setShowGrid(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.setDefaultRenderer(Object.class, new PlyRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());

                int ply = row * 2 + (col - 1);
                if(row < 0 || col < 1 || ply >= plyCount) return;

                onSelect.accept(ply + 1);
            }
        });

        add(accHeader, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setPreferredSize(new Dimension(240, 400));
    }

    public void setOnSelect(IntConsumer c) { onSelect = c; }

    public void setMoves(List<String> san, List<MoveReview> reviews) {
        tableModel.setRowCount(0);

        for(int r = 0; r * 2 < san.size(); r++) {
            String whiteMove = san.get(r*2);
            String blackMove = (r * 2 + 1 < san.size()) ? san.get(r*2 + 1) : "";

            tableModel.addRow(
                    new Object[]{
                            String.valueOf(r+1),
                            whiteMove,
                            blackMove});
        }

        this.reviews = reviews;
        plyCount = san.size();
    }

    public void setCurrent(int index) {
        current = index;
        table.repaint();
        SwingUtilities.invokeLater(() -> {
            if(index > 0) {
                int ply = index - 1;
                table.scrollRectToVisible(table.getCellRect(ply / 2, 1 + ply % 2, true));
            }
        });
    }

    public void setAccuracy(Double white, Double black) {
        if(white == null) whiteAcc.setText("White: —");
        else whiteAcc.setText(String.format(Locale.US, "White: %.1f%%", white));

        if(black == null) blackAcc.setText("Black: —");
        else blackAcc.setText(String.format(Locale.US, "Black: %.1f%%", black));
    }

    private static Color colorFor(MoveClass cls) {
        return switch (cls) {
            case BEST, EXCELLENT -> new Color(0, 130, 0);
            case GOOD -> new Color(80, 150, 80);
            case INACCURACY -> new Color(190, 150, 0);
            case MISTAKE -> new Color(220, 110, 0);
            case BLUNDER -> new Color(200, 0, 0);
        };
    }

    private class PlyRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                                                       boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(t, value, false, false, row, col);
            if(col == 0) {
                c.setBackground(t.getBackground());
                c.setForeground(t.getForeground());

                return c;
            }

            int ply = row * 2 + (col - 1);

            MoveReview review = (ply < reviews.size()) ? reviews.get(ply) : null;
            if(review != null) {
                c.setForeground(colorFor(review.cls()));
                c.setFont(c.getFont().deriveFont(Font.BOLD));
            } else {
                c.setForeground(t.getForeground());
                c.setFont(t.getFont());
            }

            if(ply == current - 1) c.setBackground(new Color(255, 230, 150));
            else c.setBackground(t.getBackground());

            return c;
        }
    }
}
