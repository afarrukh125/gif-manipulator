package com.afarrukh.giftools;

import com.github.rvesse.airline.annotations.Command;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import java.awt.Dimension;

@Command(name = "gui")
public class StartGuiCommand implements Runnable {
    @Override
    public void run() {
        Utils.setupUILookAndFeel();
        var gui = new GUIMain();

        var frame = createFrame();
        frame.setContentPane(gui.mainPanel());

        gui.createGifFromImagesButton().addActionListener(e -> Main.main("reinstate"));

        gui.createImagesFromGifButton().addActionListener(e -> Main.main("create"));
    }

    private static JFrame createFrame() {
        var frame = new JFrame("GifTools");
        frame.setIconImage(new ImageIcon(StartGuiCommand.class.getResource("/icon.png").getPath()).getImage());
        frame.setSize(new Dimension(240, 107));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
        return frame;
    }
}
