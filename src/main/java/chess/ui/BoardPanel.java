package chess.ui;

import chess.analysis.MoveClass;
import chess.model.GameModel;
import chess.model.GameResult;
import com.github.bhlangonijr.chesslib.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

public class BoardPanel extends JPanel {
    private static final String[] FILES = {"a", "b", "c", "d", "e", "f", "g", "h"};
    private static final String[] RANKS = {"1", "2", "3", "4", "5", "6", "7", "8"};

    private int cachedCoordCell = -1;
    private Font coordFont;

    private final Color bgLight = new Color(222, 184, 135);
    private final Color bgDark = new Color(139, 69, 19);
    private static final Map<Piece, String> PIECE_ICONS = new EnumMap<>(Piece.class);
    static {
        // White ♔ ♕ ♖ ♗ ♘ ♙
        PIECE_ICONS.put(Piece.WHITE_KING,    "♚");
        PIECE_ICONS.put(Piece.WHITE_QUEEN,   "♛");
        PIECE_ICONS.put(Piece.WHITE_ROOK,    "♜");
        PIECE_ICONS.put(Piece.WHITE_BISHOP,  "♝");
        PIECE_ICONS.put(Piece.WHITE_KNIGHT,  "♞");
        PIECE_ICONS.put(Piece.WHITE_PAWN,    "♟");
        // Black ♚ ♛ ♜ ♝ ♞ ♟
        PIECE_ICONS.put(Piece.BLACK_KING,    "♚");
        PIECE_ICONS.put(Piece.BLACK_QUEEN,   "♛");
        PIECE_ICONS.put(Piece.BLACK_ROOK,    "♜");
        PIECE_ICONS.put(Piece.BLACK_BISHOP,  "♝");
        PIECE_ICONS.put(Piece.BLACK_KNIGHT,  "♞");
        PIECE_ICONS.put(Piece.BLACK_PAWN,    "♟");
    }

    //===============================================
    // Badge
    private static final boolean BADGE_ON_LEFT = true;
    private Square badgeSquare;
    private MoveClass badgeClass;

    private final Map<MoveClass, BufferedImage> scaledBadges = new EnumMap<>(MoveClass.class);
    private int scaledBadgeSize = -1;
    private static final Map<MoveClass, BufferedImage> BADGE_ICONS = new EnumMap<>(MoveClass.class);
    static {
        for (MoveClass mc : MoveClass.values()) {
            try (var in = BoardPanel.class.getResourceAsStream("/badges/" + mc.name() + ".png")) {
                if (in != null) BADGE_ICONS.put(mc, ImageIO.read(in));
                else System.err.println("Badge image not found: " + mc);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setBadge(Square sq, MoveClass c) {
        if (sq == badgeSquare && c == badgeClass) return;
        badgeSquare = sq;
        badgeClass = c;
        repaint();
    }

    public void clearBadge() { setBadge(null, null); }

    private static BufferedImage resize(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage downscale(BufferedImage src, int target) {
        BufferedImage img = src;
        while (img.getWidth() / 2 >= target) {                    // зменшуємо удвічі, поки можна
            int half = img.getWidth() / 2;
            img = resize(img, half, half, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return resize(img, target, target, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private BufferedImage badgeImage(MoveClass c, int d) {
        if (d != scaledBadgeSize) {
            scaledBadges.clear();
            scaledBadgeSize = d;
        }
        return scaledBadges.computeIfAbsent(c, k -> {
            BufferedImage src = BADGE_ICONS.get(k);
            return src == null ? null : downscale(src, d);
        });
    }
    //=======================================================

    private final GameModel model;
    private MoveHandler moveHandler;
    private Side playerSide = Side.WHITE;

    private boolean flipped = false;
    private Square selected = null;
    private Set<Square> targets = Set.of();
    private boolean dragging = false;
    private int dragX, dragY;

    private Square arrowFrom, arrowTo;

    public BoardPanel(GameModel model) {
        this.model = model;
        setPreferredSize(new Dimension(640, 640));
        BoardMouseListener h = new BoardMouseListener();
        addMouseListener(h);
        addMouseMotionListener(h);
    }

    public void setMoveHandler(MoveHandler h) { this.moveHandler = h; }
    public void setPlayerSide(Side s) {
        playerSide = s;
        flipped = (s == Side.BLACK);
        clearSelection();
        repaint();
    }

    //===========================================
    // Graphics
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cell = cellSize();
        // Board cells
        for(int row = 0; row < 8; row++) {
            for(int col = 0; col < 8; col++) {
                Square sq = squareAt(row, col);
                g2.setColor((row + col) % 2 == 1 ? bgDark : bgLight);
                g2.fillRect(col * cell, row * cell, cell, cell);
                if (sq == selected) {
                    g2.setColor(new Color(255, 255, 0, 110));
                    g2.fillRect(col * cell, row * cell, cell, cell);
                }
            }
        }

        // Pieces
        for(int row = 0; row < 8; row++) {
            for(int col = 0; col < 8; col++) {
                Square sq = squareAt(row, col);
                if (dragging && sq == selected) continue;

                int centerX = col * cell + cell / 2;
                int centerY = row * cell + cell / 2;
                drawPiece(g2, model.pieceAt(sq), centerX, centerY, cell);
            }
        }

        // Targets
        for(int row = 0; row < 8; row++) {
            for(int col = 0; col < 8; col++) {
                Square sq = squareAt(row, col);
                if(!targets.contains(sq)) continue;

                int centerX = col * cell + cell / 2;
                int centerY = row * cell + cell / 2;
                g2.setColor(new Color(0, 0, 0, 70));

                if(model.pieceAt(sq) == Piece.NONE) {
                    int r = cell / 6;
                    g2.fillOval(centerX - r, centerY - r, 2 * r, 2 * r);
                } else {
                    g2.setStroke(new BasicStroke(cell / 12f));
                    int r = cell / 2 - cell / 12;
                    g2.drawOval(centerX - r, centerY - r, 2 * r, 2 * r);
                }

            }
        }

        // Arrow
        drawArrow(g2, arrowFrom, arrowTo, cell);

        // Coordinates
        if (cell != cachedCoordCell) {
            cachedCoordCell = cell;
            coordFont = new Font("DejaVu Sans", Font.PLAIN, Math.max(10, (int) (cell * 0.18)));
        }
        g2.setFont(coordFont);
        FontMetrics fm = g2.getFontMetrics();
        int pad = Math.max(3, cell / 18);

        for (int i = 0; i < 8; i++) {
            // Ranks (left column: top-left corner of each cell)
            String rank = RANKS[flipped ? i : 7 - i];
            g2.setColor(i % 2 == 0 ? bgDark : bgLight);
            g2.drawString(rank, pad, i * cell + pad + fm.getAscent());

            // Files (bottom row: bottom-right corner of each cell)
            String file = FILES[flipped ? 7 - i : i];
            g2.setColor(i % 2 == 1 ? bgDark : bgLight);
            int fileX = (i + 1) * cell - pad - fm.stringWidth(file);
            int fileY = 8 * cell - pad - fm.getDescent();
            g2.drawString(file, fileX, fileY);
        }

        // Move badges
        drawBadge(g2, cell);

        // Selected piece
        if (dragging && selected != null)
            drawPiece(g2, model.pieceAt(selected), dragX, dragY, cell);
    }

    private void drawPiece(Graphics2D g, Piece piece, int centerX, int centerY, int cell) {
        String symbol = PIECE_ICONS.get(piece);
        if (symbol == null) return;

        g.setFont(new Font("DejaVu Sans", Font.PLAIN, (int) (cell * 0.8)));
        FontMetrics fm = g.getFontMetrics();
        g.setColor((piece.getPieceSide() == Side.WHITE) ? Color.WHITE : Color.BLACK);

        int x = centerX - fm.stringWidth(symbol) / 2;
        int y = centerY - fm.getHeight() / 2 + fm.getAscent();

        g.drawString(symbol, x, y);
    }

    private void drawBadge(Graphics2D g2, int cell) {
        if (badgeSquare == null || badgeClass == null || cell <= 0) return;

        int d = (int) (cell * 0.4);
        double scale = g2.getTransform().getScaleX();
        BufferedImage img = badgeImage(badgeClass, (int) Math.round(d * scale));
        if (img == null) return;

        Point c = center(badgeSquare, cell);
        int x = BADGE_ON_LEFT ? c.x - cell / 2 : c.x + cell / 2 - d;
        int y = c.y - cell / 2;
        g2.drawImage(img, x, y, d, d, null);
    }
    //=================================================

    //==================================================
    // Support methods
    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    private Point center(Square sq, int cell) {
        int f = sq.getFile().ordinal(), r = sq.getRank().ordinal();
        int col = flipped ? 7 - f : f;
        int row = flipped ? r : 7 - r;
        return new Point(col * cell + cell / 2, row * cell + cell / 2);
    }

    private int cellSize() {
        return Math.min(getWidth(), getHeight()) / 8;
    }

    private Square squareAt(int row, int col) {
        int r = flipped ? row : 7 - row;
        int c = flipped ? 7 - col : col;
        return Square.encode(Rank.values()[r], File.values()[c]);
    }

    private Square squareAtPixel(int x, int y) {
        int cell = cellSize();

        if (cell == 0) return null;

        int col = x / cell;
        int row = y /cell;
        if(col < 0 || col > 7 || row < 0 || row > 7) return null;
        return squareAt(row, col);
    }

    private void clearSelection() {
        selected = null;
        targets = Set.of();
        dragging = false;
    }

    private void attemptMove(Square from, Square to) {
        if (moveHandler == null) return;

        Piece promotion = null;
        if (model.needsPromotion(from, to)) {
            promotion = askPromotion();
            if (promotion == null) {
                clearSelection();
                repaint();
                return;
            }
        }
        moveHandler.onMove(from, to, promotion);
        clearSelection();
        repaint();
    }

    public void showResult(GameResult r) {
        String text = resultText(r);
        if (text != null) JOptionPane.showMessageDialog(this, text);
    }

    private String resultText(GameResult r) {
        return switch (r) {
            case ONGOING    -> null;
            case WHITE_WINS -> "White wins!";
            case BLACK_WINS -> "Black wins!";
            case DRAW       -> "Draw!";
        };
    }

    private Piece askPromotion() {
        String[] options = {"Queen", "Rook", "Bishop", "Knight"};
        int choice = JOptionPane.showOptionDialog(
                this, "Choose piece", "Promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice < 0) return null;

        boolean white = model.sideToMove() == Side.WHITE;
        return switch (choice) {
            case 0 -> white ? Piece.WHITE_QUEEN  : Piece.BLACK_QUEEN;
            case 1 -> white ? Piece.WHITE_ROOK   : Piece.BLACK_ROOK;
            case 2 -> white ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
            default -> white ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
        };
    }

    // Methods for arrows
    public void setArrow(Square from, Square to) {
        if(from == arrowFrom && to == arrowTo) return;

        arrowFrom = from;
        arrowTo = to;

        repaint();
    }

    public void clearArrow() {
        arrowFrom = null;
        arrowTo = null;
        repaint();
    }

    private void drawArrow(Graphics2D g, Square from, Square to, int cell) {
        if (from == null || to == null) return;

        Point a = center(from, cell), b = center(to, cell);
        double dx = b.x - a.x, dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        if (len < 1) return;
        double ux = dx / len, uy = dy / len;          // одиничний вектор напрямку
        double headLen = cell * 0.38, half = cell * 0.2;
        double bx = b.x - ux * headLen, by = b.y - uy * headLen;   // основа наконечника

        g.setColor(new Color(60, 180, 75, 170));
        g.setStroke(new BasicStroke(cell * 0.16f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(a.x, a.y, bx, by));  // тіло: до основи наконечника

        Path2D tip = new Path2D.Double();
        tip.moveTo(b.x, b.y);                          // вістря
        tip.lineTo(bx - uy * half, by + ux * half);    // (-uy, ux) перпендикулярний до напрямку
        tip.lineTo(bx + uy * half, by - ux * half);
        tip.closePath();
        g.fill(tip);
    }
    //===================================

    //===================================
    // Mouse Events
    private class BoardMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            Square sq = squareAtPixel(e.getX(), e.getY());

            if (sq == null || !model.isLive() || model.isGameOver()) {
                clearSelection();
                repaint();
                return;
            }

            if (selected != null && targets.contains(sq)) {
                attemptMove(selected, sq);
                return;
            }

            Piece p = model.pieceAt(sq);
            if (p != Piece.NONE && p.getPieceSide() == playerSide) {
                selected = sq;
                targets = model.legalTargets(sq);
                dragging = true;
                dragX = e.getX();
                dragY = e.getY();
            } else {
                clearSelection();
            }
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!dragging) return;
            dragX = e.getX();
            dragY = e.getY();
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!dragging) return;
            dragging = false;

            Square sq = squareAtPixel(e.getX(), e.getY());
            if (sq != null && targets.contains(sq)) {
                attemptMove(selected, sq);
            } else if (sq != selected) {
                clearSelection();
            }
            repaint();
        }
    }
    //==========================================
}