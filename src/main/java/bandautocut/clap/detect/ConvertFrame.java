package bandautocut.clap.detect;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 */
public class ConvertFrame extends JFrame {
    
    private JTextField referenceRecordingFileField;
    private JTextField subjectFileField;
    private JProgressBar conversionProgressBar;
    private JButton convertButton;
    private JButton selectReferenceRecordingButton;
    private JButton selectSubjectRecordingButton;
    private Preferences prefs;

    
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        new ConvertFrame().setVisible(true);
    }
    
    public ConvertFrame() {
        super("Cut and Convert");
        prefs = Preferences.userNodeForPackage(getClass());
        initComponents();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationByPlatform(true);
        pack();
        loadComponentValues();
    }
    
    private void initComponents() {
        GridBagConstraints gbc = new GridBagConstraints();
        GridBagLayout gbl = new GridBagLayout();
        
        JPanel recordingsPanel = new JPanel();
        GridBagLayout gblp = new GridBagLayout();
        GridBagConstraints gbcp = new GridBagConstraints();
        recordingsPanel.setLayout(gblp);
        
        JLabel referenceRecordingLabel = new JLabel("Reference recording");
        gbcp.gridx = gbcp.gridy = 0;
        gbcp.anchor = GridBagConstraints.WEST;
        gblp.setConstraints(referenceRecordingLabel, gbcp);
        recordingsPanel.add(referenceRecordingLabel);
        
        referenceRecordingFileField = new JTextField(20);
        referenceRecordingFileField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                referenceRecordingUpdated(referenceRecordingFileField.getText());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                referenceRecordingUpdated(referenceRecordingFileField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                referenceRecordingUpdated(referenceRecordingFileField.getText());
            }
        });
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE;
        gbcp.fill = GridBagConstraints.HORIZONTAL;
        gbcp.weightx = 1;
        gblp.setConstraints(referenceRecordingFileField, gbcp);
        recordingsPanel.add(referenceRecordingFileField);

        selectReferenceRecordingButton = new JButton("...");
        selectReferenceRecordingButton.addActionListener(e -> {
            String path;
            File file = new File(prefs.get("reference.recording.path", ""));
            if (file.exists()) {
                path = file.getParent();
            } else {
                path = "";
            }
            SwingUtilities.invokeLater(() -> {
                selectMediaFile(referenceRecordingFileField, path);
            });
        });
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE_TRAILING;
        gbcp.fill = GridBagConstraints.NONE;
        gbcp.weightx = 0;
        gblp.setConstraints(selectReferenceRecordingButton, gbcp);
        recordingsPanel.add(selectReferenceRecordingButton);
        
        JLabel subjectRecordingLabel = new JLabel("Subject recording");
        gbcp.gridx = 0;
        gbcp.gridy++;
        gbcp.anchor = GridBagConstraints.BASELINE_LEADING;
        gbcp.fill = GridBagConstraints.NONE;
        gbcp.weightx = 0;
        gblp.setConstraints(subjectRecordingLabel, gbcp);
        recordingsPanel.add(subjectRecordingLabel);
        
        subjectFileField = new JTextField(20);
        subjectFileField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                subjectRecordingUpdated(subjectFileField.getText());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                subjectRecordingUpdated(subjectFileField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                subjectRecordingUpdated(subjectFileField.getText());
            }
        });
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE;
        gbcp.fill = GridBagConstraints.HORIZONTAL;
        gbcp.weightx = 1;
        gblp.setConstraints(subjectFileField, gbcp);
        recordingsPanel.add(subjectFileField);
        
        selectSubjectRecordingButton = new JButton("...");
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE_TRAILING;
        gbcp.fill = GridBagConstraints.NONE;
        gbcp.weightx = 0;
        gblp.setConstraints(selectSubjectRecordingButton, gbcp);
        recordingsPanel.add(selectSubjectRecordingButton);

        gbc.gridx = gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbl.setConstraints(recordingsPanel, gbc);
        getContentPane().add(recordingsPanel);
        
        convertButton = new JButton("Convert");
        convertButton.addActionListener(e -> {
            convertButton.setText("Stop");
        });

        gbc.gridy++;
        gbc.anchor = GridBagConstraints.SOUTHEAST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        gbl.setConstraints(convertButton, gbc);
        
        getContentPane().setLayout(gbl);
        getContentPane().add(convertButton);
    }
    
    private void loadComponentValues() {
        referenceRecordingFileField.setText(prefs.get("reference.recording.path", ""));
        subjectFileField.setText(prefs.get("subject.recording.path", ""));
    }
    
    private void referenceRecordingUpdated(String value) {
        prefs.put("reference.recording.path", value);
    }
    
    private void subjectRecordingUpdated(String value) {
        prefs.put("subject.recording.path", value);
    }
    
    private void selectMediaFile(JTextField textField, String currentDirectoryPath) {
        JFileChooser fileChooser = new JFileChooser(currentDirectoryPath);
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("Media Files", "mov", "mp4", "mpg", "avi", "qt", "mpeg"));
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            textField.setText(selectedFile.getAbsolutePath());
        }
    }
}
