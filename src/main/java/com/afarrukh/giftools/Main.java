package com.afarrukh.giftools;

import com.github.rvesse.airline.builder.CliBuilder;
import com.github.rvesse.airline.help.Help;

public class Main {
    public static void main(String[] args) {
        var cli = new CliBuilder<Runnable>("giftools")
                .withDescription("Extract, rebuild and edit GIFs")
                .withDefaultCommand(ServeCommand.class)
                .withCommand(ServeCommand.class)
                .withCommand(CreateCommand.class)
                .withCommand(ReinstateCommand.class)
                .withCommand(Help.class)
                .build();
        cli.parse(args).run();
    }
}
