package bandautocut.clap.detect;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import javax.swing.JPanel;

/**
 *
 */
public class SampleViewer extends JPanel {
    
    private List<SampleSource> sources;

    @Override
    protected void paintComponent(Graphics g1) {
        Graphics2D g = (Graphics2D) g1;
    }
}
