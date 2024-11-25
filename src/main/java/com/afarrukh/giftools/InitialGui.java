package com.afarrukh.giftools;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class InitialGui {
    private JPanel mainPanel;
    private JLabel titleLabel;
    private JButton createImagesFromGifButton;
    private JButton createGifFromImagesButton;
    private JButton manipulateGifButton;

    public JPanel mainPanel() {
        return mainPanel;
    }

    public JButton createImagesFromGifButton() {
        return createImagesFromGifButton;
    }

    public JButton createGifFromImagesButton() {
        return createGifFromImagesButton;
    }

    public JButton manipulateGifButton() {
        return manipulateGifButton;
    }
}
