package bandautocut.clap.detect;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.SystemColor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 *
 */
public class SampleViewer extends JPanel implements Scrollable, MouseListener, MouseMotionListener {
    
    private final List<SampleSource> sources = new ArrayList<>(4);
    private final Dimension minimumSize = new Dimension(100, 80);
    private final Dimension size = new Dimension(0, 80);
    private final List<Consumer<DragEvent>> dragListeners = new LinkedList<>();

    private float zoomFactor = 1;
    
    private int dragStartX;
    private boolean dragging;

    @Override
    public void doLayout() {
        super.doLayout();
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override
    protected void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g = (Graphics2D) g1;
        int width = getSize().width;
        int height = getSize().height;
        if (!dragging) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        Rectangle bounds = g.getClipBounds();

        if (width <= 0 || height <= 0) {
            return;
        }

        g.setColor(SystemColor.windowText);
        if (bounds == null) {
            bounds = new Rectangle(width, height);
        }
        if (this.sources.isEmpty()) {
            return;
        }
        int yh = height / this.sources.size();
        int[][] xPoints = new int[this.sources.size()][];
        int[][] yPoints = new int[this.sources.size()][];
        for (int sourceIdx = 0; sourceIdx < this.sources.size(); sourceIdx++) {
            int y = yh * sourceIdx + yh / 2;
            SampleSource source = this.sources.get(sourceIdx);
            int previousDistance = (int) (source.sampleAt(bounds.x - 1) / (float) Short.MAX_VALUE * yh / 2);
            xPoints[sourceIdx] = new int[bounds.width + 1];
            yPoints[sourceIdx] = new int[bounds.width + 1];
            xPoints[sourceIdx][0] = bounds.x - 1;
            yPoints[sourceIdx][0] = y + previousDistance;
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                short sample = source.sampleAt(x);
                int currentDistance = (int) (sample / (float) Short.MAX_VALUE * yh / 2);
                xPoints[sourceIdx][x - bounds.x + 1] = x;
                yPoints[sourceIdx][x - bounds.x + 1] = y + currentDistance;
            }
            g.drawPolyline(xPoints[sourceIdx], yPoints[sourceIdx], xPoints[sourceIdx].length);
        }
        
        g.setColor(SystemColor.windowBorder);
        for (int i = 1; i < this.sources.size(); i++) {
            int y = bounds.y + yh * i;
            g.drawLine(bounds.x, y, bounds.x + width, y);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return size;
    }

    @Override
    public Dimension getMinimumSize() {
        return minimumSize;
    }
    
    public void addSampleSource(SampleSource source) {
        this.sources.add(source);
        size.height = Math.min(40, sources.size() * 20);
        size.width = Math.max(size.width, source.size());
        revalidate();
    }
    
    public void removeAllSources() {
        this.sources.clear();
        size.width = 0;
        revalidate();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getMinimumSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 3;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 100;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        dragStartX = e.getX();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int distance = e.getX() - dragStartX;
        if (distance > 0) {
            fireDragEvent(distance, true);
        }
        dragging = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int distance = e.getX() - dragStartX;
        fireDragEvent(distance, false);
        dragging = true;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }
    
    private void fireDragEvent(int distance, boolean done) {
        for (Consumer<DragEvent> listener : dragListeners) {
            listener.accept(new DragEvent(distance, done));
        }
    }
    
    public void addDragListener(Consumer<DragEvent> listener) {
        this.dragListeners.add(listener);
    }
    
    public static class DragEvent {

        private int distance;
        private boolean done;

        public DragEvent() {
        }

        public DragEvent(int distance, boolean done) {
            this.distance = distance;
            this.done = done;
        }
        
        public int getDistance() {
            return distance;
        }

        public void setDistance(int distance) {
            this.distance = distance;
        }

        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }
    }
}
