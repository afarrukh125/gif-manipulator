package com.afarrukh.giftools;

import com.github.rvesse.airline.builder.CliBuilder;

public class Main {
    public static void main(String[] args) {
        var cli = new CliBuilder<Runnable>("giftools")
                .withCommand(CreateCommand.class)
                .withCommand(ReinstateCommand.class)
                .build();
        cli.parse(args).run();
    }
}
