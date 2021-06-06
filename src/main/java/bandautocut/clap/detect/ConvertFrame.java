package bandautocut.clap.detect;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.NullOutput;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;

import java.awt.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 */
public final class ConvertFrame extends JFrame implements Consumer<SampleViewer.DragEvent> {
    
    private final Preferences prefs;
    private final AudioResampler resampler;
    private final LoudnessDetection loudnessDetection;
    
    private boolean dragging = false;
    private int offsetStart;
    
    private ShortBuffer referenceBuf;
    private ByteBuffer referenceByteBuf;
    private ByteBuffer subjectByteBuf;
    private ShortBuffer subjectBuf;
    private ShortBufferSampleSource referenceSampleSource;
    private ShortBufferSampleSource subjectSampleSource;
    
    private JTextField referenceRecordingFileField;
    private JTextField subjectFileField;
    private JTextField referenceRecordingOffsetSecondsField;
    private JTextField referenceRecordingOffsetFramesField;
    private JLabel clapPositionLabel;
    private JProgressBar conversionProgressBar;
    private JButton convertButton;
    private JButton selectReferenceRecordingButton;
    private JButton selectSubjectRecordingButton;
    private JButton loadButton;
    private SampleViewer sampleViewer;
    private JScrollPane sampleViewerPane;
    
    private JPanel clapDetectionPanel;
    private JTextField thresholdConstantField;
    private JTextField decisionThresholdField;
    private JTextField shortTermDurationField;
    private JTextField longTermDurationField;
    private JTextField maxAllowedClapDurationField;
    private JButton detectClapsButton;

    private JCheckBox referenceEnabledCheckBox;
    private JCheckBox subjectEnabledCheckBox;
    private JButton playButton;
    private JButton scrollButton;
    
    private JTextField positionTextField;


    
    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        new ConvertFrame(new AudioResampler(), new LoudnessDetection()).setVisible(true);
    }
    
    public ConvertFrame(AudioResampler resampler, LoudnessDetection loudnessDetection) {
        super("Cut and Convert");
        this.resampler = resampler;
        this.loudnessDetection = loudnessDetection;
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

        loadButton = new JButton("Load");
        loadButton.addActionListener(l -> {
            loadButton.setEnabled(false);
            new Thread() {
                @Override
                public void run() {
                    try {
                        ProgressMonitor progressMonitor = new ProgressMonitor(ConvertFrame.this, "Reading audio", "", 0, 2);
                        progressMonitor.setMillisToDecideToPopup(0);
                        progressMonitor.setMillisToPopup(1);
                        referenceByteBuf = resampler.resampleAudio(referenceRecordingFileField.getText(), 1, 8000)
                                .order(ByteOrder.nativeOrder());
                        referenceBuf = referenceByteBuf
                                .asShortBuffer();
                        progressMonitor.setProgress(1);
                        subjectByteBuf = resampler.resampleAudio(subjectFileField.getText(), 1, 8000)
                                .order(ByteOrder.nativeOrder());
                        subjectBuf = subjectByteBuf
                                .asShortBuffer();
                        progressMonitor.setProgress(2);
                        
                        float subjectLoudnessMultiplier = loudnessDetection.suggestMultiplier(subjectBuf);
                        
                        SwingUtilities.invokeLater(() -> {
                            sampleViewer.removeAllSources();
                            referenceSampleSource = new ShortBufferSampleSource("Reference", referenceBuf, 1);
                            subjectSampleSource = new ShortBufferSampleSource("Subject", subjectBuf, subjectLoudnessMultiplier);
                            sampleViewer.addSampleSource(referenceSampleSource);
                            sampleViewer.addSampleSource(subjectSampleSource);
                            sampleViewerPane.revalidate();
                            sampleViewerPane.repaint();
                            clapPositionLabel.setText("Subject loudness multiplier: " + String.format("%.2f", subjectLoudnessMultiplier));
                        });
                    } finally {
                        SwingUtilities.invokeLater(() -> {
                            loadButton.setEnabled(true);
                        });
                    }
                }
            }.start();
        });
        gbcp.gridx++;
        gbcp.gridheight = 2;
        gbcp.weighty = 1;
        gbcp.fill = GridBagConstraints.BOTH;
        gblp.setConstraints(loadButton, gbcp);
        recordingsPanel.add(loadButton);

        JLabel subjectRecordingLabel = new JLabel("Subject recording");
        gbcp.gridx = 0;
        gbcp.gridheight = 1;
        gbcp.gridy++;
        gbcp.anchor = GridBagConstraints.BASELINE_LEADING;
        gbcp.fill = GridBagConstraints.NONE;
        gbcp.weightx = 0;
        gbcp.weighty = 0;
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
        selectSubjectRecordingButton.addActionListener(e -> {
            String path;
            File file = new File(prefs.get("subject.recording.path", ""));
            if (file.exists()) {
                path = file.getParent();
            } else {
                path = "";
            }
            SwingUtilities.invokeLater(() -> {
                selectMediaFile(subjectFileField, path);
            });
        });
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE_TRAILING;
        gbcp.fill = GridBagConstraints.NONE;
        gbcp.weightx = 0;
        gblp.setConstraints(selectSubjectRecordingButton, gbcp);
        recordingsPanel.add(selectSubjectRecordingButton);

        recordingsPanel.setBorder(BorderFactory.createTitledBorder(null, "Recordings"));
        gbc.gridx = gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;
        gbc.gridwidth = 2;
        gbl.setConstraints(recordingsPanel, gbc);
        getContentPane().add(recordingsPanel);
        
        sampleViewer = new SampleViewer();
        sampleViewer.addDragListener(this);
        sampleViewerPane = new JScrollPane(sampleViewer);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbl.setConstraints(sampleViewerPane, gbc);
        getContentPane().add(sampleViewerPane);
        
        clapDetectionPanel = new JPanel();
        clapDetectionPanel.setBorder(BorderFactory.createTitledBorder("Clap Detection"));
        gblp = new GridBagLayout();
        gbcp = new GridBagConstraints();
        clapDetectionPanel.setLayout(gblp);
        gbcp.gridx = 0;
        gbcp.gridy = 0;
        gbcp.anchor = GridBagConstraints.BASELINE_LEADING;
        JLabel thresholdConstantLabel = new JLabel("Threshold constant");
        gblp.setConstraints(thresholdConstantLabel, gbcp);
        clapDetectionPanel.add(thresholdConstantLabel);
        
        gbcp.gridy++;
        JLabel decisionThresholdLabel = new JLabel("Decision threshold");
        gblp.setConstraints(decisionThresholdLabel, gbcp);
        clapDetectionPanel.add(decisionThresholdLabel);
        
        gbcp.gridy++;
        JLabel shortTermDurationLabel = new JLabel("Short term duration");
        gblp.setConstraints(shortTermDurationLabel, gbcp);
        clapDetectionPanel.add(shortTermDurationLabel);
        
        gbcp.gridy++;
        JLabel longTermDurationLabel = new JLabel("Long term duration");
        gblp.setConstraints(longTermDurationLabel, gbcp);
        clapDetectionPanel.add(longTermDurationLabel);
        
        gbcp.gridy++;
        JLabel maxAllowedClapDurationLabel = new JLabel("Max allowed clap duration");
        gblp.setConstraints(maxAllowedClapDurationLabel, gbcp);
        clapDetectionPanel.add(maxAllowedClapDurationLabel);
        
        thresholdConstantField = new JTextField(20);
        thresholdConstantField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    thresholdConstantChanged(Integer.parseInt(thresholdConstantField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    thresholdConstantChanged(Integer.parseInt(thresholdConstantField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    thresholdConstantChanged(Integer.parseInt(thresholdConstantField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gbcp.gridx++;
        gbcp.gridy = 0;
        gbcp.fill = GridBagConstraints.BOTH;
        gbcp.weightx = 1;
        gbcp.anchor = GridBagConstraints.BASELINE;
        gblp.setConstraints(thresholdConstantField, gbcp);
        clapDetectionPanel.add(thresholdConstantField);
        
        gbcp.gridy++;
        decisionThresholdField = new JTextField(20);
        decisionThresholdField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    decisionThresholdChanged(Integer.parseInt(decisionThresholdField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    decisionThresholdChanged(Integer.parseInt(decisionThresholdField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    decisionThresholdChanged(Integer.parseInt(decisionThresholdField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gblp.setConstraints(decisionThresholdField, gbcp);
        clapDetectionPanel.add(decisionThresholdField);
        
        gbcp.gridy++;
        shortTermDurationField = new JTextField(20);
        shortTermDurationField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    shortTermDurationChanged(Integer.parseInt(shortTermDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    shortTermDurationChanged(Integer.parseInt(shortTermDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    shortTermDurationChanged(Integer.parseInt(shortTermDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gblp.setConstraints(shortTermDurationField, gbcp);
        clapDetectionPanel.add(shortTermDurationField);
        
        gbcp.gridy++;
        longTermDurationField = new JTextField(20);
        longTermDurationField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    longTermDurationChanged(Integer.parseInt(longTermDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    longTermDurationChanged(Integer.parseInt(longTermDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    longTermDurationChanged(Integer.parseInt(longTermDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gblp.setConstraints(longTermDurationField, gbcp);
        clapDetectionPanel.add(longTermDurationField);
        
        gbcp.gridy++;
        maxAllowedClapDurationField = new JTextField(20);
        maxAllowedClapDurationField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    maxAllowedClapLengthChanged(Integer.parseInt(maxAllowedClapDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    maxAllowedClapLengthChanged(Integer.parseInt(maxAllowedClapDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    maxAllowedClapLengthChanged(Integer.parseInt(maxAllowedClapDurationField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gblp.setConstraints(maxAllowedClapDurationField, gbcp);
        clapDetectionPanel.add(maxAllowedClapDurationField);
        
        detectClapsButton = new JButton("Detect claps");
        detectClapsButton.addActionListener(e -> {
            if (subjectBuf == null) {
                clapPositionLabel.setText("No recordings loaded");
                return;
            }
            ACDRClapDetect.Parameters params = new ACDRClapDetect.Parameters();
            try {
                params.setThresholdConstant(Integer.parseInt(thresholdConstantField.getText()));
            } catch (NumberFormatException ex) {}
            try {
                params.setDecisionThreshold(Integer.parseInt(decisionThresholdField.getText()));
            } catch (NumberFormatException ex) {}
            try {
                params.setShortTermDuration(Integer.parseInt(shortTermDurationField.getText()));
            } catch (NumberFormatException ex) {}
            try {
                params.setLongTermDuration(Integer.parseInt(longTermDurationField.getText()));
            } catch (NumberFormatException ex) {}
            try {
                params.setMaxAllowedClapDuraton(Integer.parseInt(maxAllowedClapDurationField.getText()));
            } catch (NumberFormatException ex) {}

            ClapDetectResult result = new ACDRClapDetect(subjectBuf, params).clapDetect(4);
            if (result.getBestPosition() == 0) {
                clapPositionLabel.setText("Unable to detect claps");
            } else {
                clapPositionLabel.setText("Clap position: " + String.format("%.2fs", result.getBestPosition() / 8000f));
                positionTextField.setText(String.valueOf(result.getBestPosition()));
            }
        });
        gbcp.gridx = 0;
        gbcp.gridy++;
        gbcp.fill = GridBagConstraints.NONE;
        gbcp.weightx = 0;
        gbcp.anchor = GridBagConstraints.BASELINE;
        gblp.setConstraints(detectClapsButton, gbcp);
        clapDetectionPanel.add(detectClapsButton);
        
        clapPositionLabel = new JLabel("");
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE_LEADING;
        gblp.setConstraints(clapPositionLabel, gbcp);
        clapDetectionPanel.add(clapPositionLabel);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weighty = 0;
        gbc.gridwidth = 1;
        gbc.gridheight = 2;
        gbl.setConstraints(clapDetectionPanel, gbc);
        getContentPane().add(clapDetectionPanel);
        
        JPanel positionPanel = new JPanel();
        positionPanel.setBorder(BorderFactory.createTitledBorder("Position"));
        
        gblp = new GridBagLayout();
        gbcp = new GridBagConstraints();
        gbcp.gridx = 0;
        gbcp.gridy = 0;
        positionPanel.setLayout(gblp);
        
        JLabel referenceRecordingOffsetLabel = new JLabel("Ref. Offset");
        gbcp.anchor = GridBagConstraints.BASELINE_LEADING;
        gblp.setConstraints(referenceRecordingOffsetLabel, gbcp);
        positionPanel.add(referenceRecordingOffsetLabel);
        
        gbcp.gridx++;
        gbcp.fill = GridBagConstraints.HORIZONTAL;
        referenceRecordingOffsetSecondsField = new JTextField(3);
        referenceRecordingOffsetSecondsField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    referenceStartSecondsUpdated(Integer.parseInt(referenceRecordingOffsetSecondsField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    referenceStartSecondsUpdated(Integer.parseInt(referenceRecordingOffsetSecondsField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    referenceStartSecondsUpdated(Integer.parseInt(referenceRecordingOffsetSecondsField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gblp.setConstraints(referenceRecordingOffsetSecondsField, gbcp);
        positionPanel.add(referenceRecordingOffsetSecondsField);
        
        gbcp.gridx++;
        gbcp.anchor = GridBagConstraints.BASELINE;
        referenceRecordingOffsetFramesField = new JTextField(3);
        referenceRecordingOffsetFramesField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    referenceStartFramesUpdated(Integer.parseInt(referenceRecordingOffsetFramesField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    referenceStartFramesUpdated(Integer.parseInt(referenceRecordingOffsetFramesField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    referenceStartFramesUpdated(Integer.parseInt(referenceRecordingOffsetFramesField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gblp.setConstraints(referenceRecordingOffsetFramesField, gbcp);
        positionPanel.add(referenceRecordingOffsetFramesField);
        
        JLabel positionLabel = new JLabel("Cut frame");
        gbcp.anchor = GridBagConstraints.BASELINE_LEADING;
        gbcp.gridx = 0;
        gbcp.gridy++;
        gbcp.gridwidth = 1;
        gblp.setConstraints(positionLabel, gbcp);
        positionPanel.add(positionLabel);
        
        positionTextField = new JTextField(10);
        positionTextField.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                try {
                    positionFieldChanged(Integer.parseInt(positionTextField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                try {
                    positionFieldChanged(Integer.parseInt(positionTextField.getText()));
                } catch (NumberFormatException ex) {}
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                try {
                    positionFieldChanged(Integer.parseInt(positionTextField.getText()));
                } catch (NumberFormatException ex) {}
            }
        });
        gbcp.gridx++;
        gbcp.weightx = 1;
        gbcp.gridwidth = 3;
        gbcp.fill = GridBagConstraints.HORIZONTAL;
        gbcp.anchor = GridBagConstraints.BASELINE;
        gblp.setConstraints(positionTextField, gbcp);
        positionPanel.add(positionTextField);
        
        
        gbc.gridx++;
        gbc.weightx = 0;
        gbc.gridheight = 1;
        gbl.setConstraints(positionPanel, gbc);
        getContentPane().add(positionPanel);
        
        JPanel previewPanel = new JPanel();
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
        
        gblp = new GridBagLayout();
        gbcp = new GridBagConstraints();
        gbcp.gridx = 0;
        gbcp.gridy = 0;
        previewPanel.setLayout(gblp);
        
        referenceEnabledCheckBox = new JCheckBox("Reference", false);
        gblp.setConstraints(referenceEnabledCheckBox, gbcp);
        previewPanel.add(referenceEnabledCheckBox);
        
        subjectEnabledCheckBox = new JCheckBox("Subject", true);
        gbcp.gridy++;
        gblp.setConstraints(subjectEnabledCheckBox, gbcp);
        previewPanel.add(subjectEnabledCheckBox);
        
        gbcp.gridx++;
        gbcp.gridy = 0;
        playButton = new JButton("Play");
        playButton.addActionListener(e -> {
            if (subjectByteBuf == null) {
                return;
            }
            playButton.setEnabled(false);
            int position = 0;
            try {
                position = Integer.parseInt(positionTextField.getText());
            } catch (NumberFormatException ex) {
            }
            boolean referenceEnabled = referenceEnabledCheckBox.isSelected();
            boolean subjectEnabled = subjectEnabledCheckBox.isSelected();
            int pos = position;
            new Thread() {
                @Override
                public void run() {
                    AudioFormat audioFormat = new AudioFormat(8000, 16, 1, true, referenceBuf.order() == ByteOrder.BIG_ENDIAN);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                    try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                        line.open(audioFormat, 4096);
                        line.start();
                        byte[] referenceBytes = referenceByteBuf.array();
                        int referenceOffset = referenceByteBuf.arrayOffset();
                        byte[] subjectBytes = subjectByteBuf.array();
                        int subjectOffset = subjectByteBuf.arrayOffset();
                        byte[] tempBytes = null;
                        ByteBuffer tempByteBuf = null;
                        ShortBuffer tempBuf = null;
                        int refStart = referenceRecordingStartSample();
                        for (int i = Math.max(0, pos - 20000); i < pos + 1000; i += 2048) {
                            if (referenceEnabled && !subjectEnabled) {
                                line.write(referenceBytes, referenceOffset + (i - pos + refStart) * 2, 4096);
                            } else if (subjectEnabled && !referenceEnabled) {
                                line.write(subjectBytes, subjectOffset + i * 2, 4096);
                            } else if (subjectEnabled && referenceEnabled) {
                                // Both enabled; mix the two audio sources together
                                if (tempBytes == null) {
                                    tempBytes = new byte[4096];
                                    tempByteBuf = ByteBuffer.wrap(tempBytes).order(subjectByteBuf.order());
                                    tempBuf = tempByteBuf.asShortBuffer();
                                }
                                assert tempBuf != null;
                                tempBuf.clear();
                                for (int j = 0; j < 2048; j++) {
                                    short referenceSample = referenceBuf.get(i - pos + refStart + j);
                                    short subjectSample = subjectBuf.get(i + j);
                                    short sample = (short) (((int) referenceSample + subjectSample) / 2);
                                    tempBuf.put(sample);
                                }
                                tempBuf.flip();
                                line.write(tempBytes, 0, 4096);
                            }
                        }
                        line.drain();
                        line.stop();
                        SwingUtilities.invokeLater(() -> playButton.setEnabled(true));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }.start();
        });
        gblp.setConstraints(playButton, gbcp);
        previewPanel.add(playButton);
        
        gbcp.gridy++;
        scrollButton = new JButton("Scroll");
        scrollButton.addActionListener(e -> {
            try {
                int position = Integer.parseInt(positionTextField.getText());
                Rectangle rect = new Rectangle(position, 0, 1, 1);
                sampleViewer.scrollRectToVisible(rect);
            } catch (NumberFormatException ex) {}
        });
        gblp.setConstraints(scrollButton, gbcp);
        previewPanel.add(scrollButton);
        
        gbc.gridy++;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbl.setConstraints(previewPanel, gbc);
        getContentPane().add(previewPanel);

        conversionProgressBar = new JProgressBar();
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbl.setConstraints(conversionProgressBar, gbc);
        getContentPane().add(conversionProgressBar);
        
        convertButton = new JButton("Convert");
        convertButton.addActionListener(e -> {
            convertButton.setText("Stop");
            conversionProgressBar.setIndeterminate(true);
            
            File inputFile = new File(subjectFileField.getText());
            File outputFile = new File(inputFile.getParentFile(), inputFile.getName().replaceAll("\\..+$", "") + "_auto.mp4");
            
            int position = Integer.parseInt(positionTextField.getText());
            new Thread(() -> {
                final AtomicLong atomicDuration = new AtomicLong();
                FFmpeg.atPath()
                        .addInput(UrlInput.fromPath(Path.of(inputFile.getAbsolutePath())))
                        .setOverwriteOutput(true)
                        .addOutput(new NullOutput())
                        .setProgressListener(progress -> {
                            atomicDuration.set(progress.getTimeMillis());
                        })
                        .execute();

                int duration = atomicDuration.intValue();

                Taskbar taskbar;
                if (Taskbar.isTaskbarSupported()) {
                    taskbar = Taskbar.getTaskbar();
                    taskbar.setWindowProgressState(ConvertFrame.this, Taskbar.State.NORMAL);
                } else {
                    taskbar = null;
                }

                SwingUtilities.invokeLater(() -> {
                    conversionProgressBar.setIndeterminate(false);
                    conversionProgressBar.setMinimum(position / 8);
                    conversionProgressBar.setMaximum(duration);
                });
                FFmpeg.atPath()
                        .addInput(
                                UrlInput.fromPath(Path.of(inputFile.getAbsolutePath()))
                                        .setPosition(position / 8)
                        )
                        .setOverwriteOutput(true)
                        .addArguments("-movflags", "faststart")
                        .setFilter("v", "scale='min(1920,iw)':min'(1080,ih)':force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2")
                        .addOutput(UrlOutput.toUrl(outputFile.getAbsolutePath()))
                        .setProgressListener(progress -> {
                            SwingUtilities.invokeLater(() -> {
                                int timeValue = progress.getTimeMillis().intValue();
                                conversionProgressBar.setValue(timeValue);
                                if (taskbar != null) {
                                    int percentage = (100 * (timeValue - (position / 8))) / (duration - (position / 8));
                                    taskbar.setWindowProgressValue(ConvertFrame.this, percentage);
                                }
                            });
                        })
                        .execute();
                SwingUtilities.invokeLater(() -> {
                    convertButton.setText("Convert");
                    conversionProgressBar.setValue(0);
                    if (taskbar != null) {
                        taskbar.setWindowProgressState(ConvertFrame.this, Taskbar.State.OFF);
                    }
                });
            }).start();
        });

        gbc.gridx++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.BASELINE;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbl.setConstraints(convertButton, gbc);
        
        getContentPane().setLayout(gbl);
        getContentPane().add(convertButton);
    }
    
    private void loadComponentValues() {
        referenceRecordingFileField.setText(prefs.get("reference.recording.path", ""));
        subjectFileField.setText(prefs.get("subject.recording.path", ""));
        referenceRecordingOffsetSecondsField.setText(prefs.get("reference.recording.start.seconds", ""));
        referenceRecordingOffsetFramesField.setText(prefs.get("reference.recording.start.frames", ""));
        thresholdConstantField.setText(prefs.get("clapdetect.threshold.constant", ""));
        decisionThresholdField.setText(prefs.get("clapdetect.decision.threshold", ""));
        shortTermDurationField.setText(prefs.get("clapdetect.shortterm.duration", ""));
        longTermDurationField.setText(prefs.get("clapdetect.longterm.duration", ""));
        maxAllowedClapDurationField.setText(prefs.get("clapdetect.clap.maxlength", ""));
    }
    
    private void referenceRecordingUpdated(String value) {
        prefs.put("reference.recording.path", value);
    }
    
    private void subjectRecordingUpdated(String value) {
        prefs.put("subject.recording.path", value);
    }
    
    private void selectMediaFile(JTextField textField, String currentDirectoryPath) {
        JFileChooser fileChooser = new JFileChooser(currentDirectoryPath);
        FileFilter fileFilter = new FileNameExtensionFilter("Media Files", "mov", "mp4", "mpg", "avi", "qt", "mpeg");
        fileChooser.addChoosableFileFilter(fileFilter);
        fileChooser.setFileFilter(fileFilter);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            textField.setText(selectedFile.getAbsolutePath());
        }
    }
    
    int referenceRecordingStartSample() {
        int startSeconds = 0;
        int startFrames = 0;
        try {
            startSeconds = Integer.parseInt(referenceRecordingOffsetSecondsField.getText());
            startFrames = Integer.parseInt(referenceRecordingOffsetFramesField.getText());
        } catch (NumberFormatException ex) {
        }
        return startSeconds * 8000 + startFrames * 8000 / 30;
    }

    private void referenceStartSecondsUpdated(int seconds) {
        prefs.put("reference.recording.start.seconds", String.valueOf(seconds));
    }
    
    private void referenceStartFramesUpdated(int frames) {
        prefs.put("reference.recording.start.frames", String.valueOf(frames));
    }
    
    void setCutPosition(int frame) {
        Runnable r = () -> {
            String text = String.valueOf(frame);
            if (!positionTextField.getText().equals(text)) {
                positionTextField.setText(text);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
    
    private void positionFieldChanged(int position) {
        subjectSampleSource.setOffset(referenceRecordingStartSample() - position);
        sampleViewer.revalidate();
        sampleViewer.repaint();
        clapPositionLabel.setText(String.format("Position set to %.2fs", position / 8000f));
//        Rectangle rect = new Rectangle(position - sampleViewerPane.getWidth(), 0, sampleViewerPane.getWidth(), sampleViewerPane.getHeight());
//        sampleViewer.scrollRectToVisible(rect);
    }
    
    private void thresholdConstantChanged(int thresholdConstant) {
        prefs.put("clapdetect.threshold.constant", String.valueOf(thresholdConstant));
    }
    
    private void decisionThresholdChanged(int decisionThreshold) {
        prefs.put("clapdetect.decision.threshold", String.valueOf(decisionThreshold));
    }
    
    private void shortTermDurationChanged(int shortTermDuraton) {
        prefs.put("clapdetect.shortterm.duration", String.valueOf(shortTermDuraton));
    }
    
    private void longTermDurationChanged(int longTermDuration) {
        prefs.put("clapdetect.longterm.duration", String.valueOf(longTermDuration));
    }
    
    private void maxAllowedClapLengthChanged(int maxAllowedClapLength) {
        prefs.put("clapdetect.clap.maxlength", String.valueOf(maxAllowedClapLength));
    }

    @Override
    public void accept(SampleViewer.DragEvent e) {
        if (e.isDone()) {
            dragging = false;
        } else if (dragging) {
        } else {
            try {
                offsetStart = Integer.parseInt(positionTextField.getText());
            } catch (NumberFormatException ex) {
                offsetStart = 0;
            }
            dragging = true;
        }
        String newValue = String.valueOf(offsetStart - e.getDistance());
        positionTextField.setText(newValue);
    }
}
