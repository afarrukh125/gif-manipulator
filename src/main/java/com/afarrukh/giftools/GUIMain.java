package com.afarrukh.giftools;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class GUIMain {
    private JPanel mainPanel;
    private JLabel titleLabel;
    private JButton createImagesFromGifButton;
    private JButton createGifFromImagesButton;

    public JPanel mainPanel() {
        return mainPanel;
    }

    public JButton createImagesFromGifButton() {
        return createImagesFromGifButton;
    }

    public JButton createGifFromImagesButton() {
        return createGifFromImagesButton;
    }
}
