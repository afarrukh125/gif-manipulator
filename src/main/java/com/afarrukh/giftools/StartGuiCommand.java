package com.afarrukh.giftools;

import com.github.rvesse.airline.annotations.Command;

import javax.swing.JFrame;
import java.awt.Dimension;

@Command(name = "gui")
public class StartGuiCommand implements Runnable {
    @Override
    public void run() {
        Utils.setupUILookAndFeel();
        var gui = new GUIMain();
        var frame = new JFrame("GifTools");
        frame.setSize(new Dimension(800, 600));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(gui.mainPanel());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
