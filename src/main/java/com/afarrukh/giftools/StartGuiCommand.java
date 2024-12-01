package com.afarrukh.giftools;

import com.github.rvesse.airline.annotations.Command;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;

@Command(name = "gui")
public class StartGuiCommand implements Runnable {

    private int numButtons;

    @Override
    public void run() {
        Utils.setupUILookAndFeel();
        var initialGui = new InitialGui();

        var frame = createFrame();
        frame.setContentPane(initialGui.mainPanel());

        addListenerToButton(initialGui.createGifFromImagesButton(), e -> Main.main("reinstate"));

        addListenerToButton(initialGui.createImagesFromGifButton(), e -> Main.main("create"));

        addListenerToButton(initialGui.manipulateGifButton(), e -> Main.main("manipulate"));

        frame.setSize(new Dimension(240, 40 + (40 * numButtons)));
        frame.setLocationRelativeTo(null);
    }

    private void addListenerToButton(JButton button, ActionListener actionListener) {
        button.addActionListener(actionListener);
        this.numButtons++;
    }

    private static JFrame createFrame() {
        var frame = new JFrame("GifTools");
        frame.setIconImage(
                new ImageIcon(StartGuiCommand.class.getResource("/icon.png").getPath()).getImage());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        return frame;
    }
}
