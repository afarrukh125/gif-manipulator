package com.afarrukh.giftools;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends the log to a file instead of the console, for the editor started at login: launched with javaw there is
 * no console for logback.xml's appender to write to, so anything it says is lost.
 */
final class FileLogging {

    private static final Logger LOG = LoggerFactory.getLogger(FileLogging.class);

    private static final String PATTERN = "[%d{yyyy.MM.dd HH:mm:ss.SSS}] [%thread] %-5level %logger{36} - %msg%n";

    private FileLogging() {}

    static void sendLogsTo(Path file) {
        var target = file.toAbsolutePath();
        var parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LOG.warn("Cannot write {} ({}), logging to the console instead", target, e.getMessage());
            return;
        }

        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var appender = rollingAppender(context, target);
        if (!appender.isStarted()) {
            LOG.warn("Cannot write {}, logging to the console instead", target);
            return;
        }

        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.addAppender(appender);
        LOG.info("Logging to {}", target);
    }

    private static RollingFileAppender<ILoggingEvent> rollingAppender(LoggerContext context, Path target) {
        var encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(PATTERN);
        encoder.start();

        var appender = new RollingFileAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName("File");
        appender.setFile(target.toString());
        appender.setEncoder(encoder);

        // Left running from login this would otherwise grow without end.
        var rolling = new FixedWindowRollingPolicy();
        rolling.setContext(context);
        rolling.setParent(appender);
        rolling.setFileNamePattern(target + ".%i");
        rolling.setMinIndex(1);
        rolling.setMaxIndex(2);
        rolling.start();
        appender.setRollingPolicy(rolling);

        var triggering = new SizeBasedTriggeringPolicy<ILoggingEvent>();
        triggering.setContext(context);
        triggering.setMaxFileSize(FileSize.valueOf("1MB"));
        triggering.start();
        appender.setTriggeringPolicy(triggering);

        appender.start();
        return appender;
    }
}
