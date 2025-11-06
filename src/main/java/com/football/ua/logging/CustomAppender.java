package com.football.ua.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class CustomAppender extends AppenderBase<ILoggingEvent> {

    private String fileName = "logs/custom-app.log";
    private Path logFilePath;
    private ch.qos.logback.core.Layout<ILoggingEvent> layout;

    @Override
    public void start() {
        try {
            logFilePath = Paths.get(fileName);
            if (logFilePath.getParent() != null) {
                Files.createDirectories(logFilePath.getParent());
            }
            if (!Files.exists(logFilePath)) {
                Files.createFile(logFilePath);
            }
            super.start();
        } catch (IOException e) {
            addError("Не вдалося створити файл логів: " + fileName, e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        try {
            String formattedMessage = layout != null 
                ? layout.doLayout(event) 
                : event.getFormattedMessage() + "\n";

            System.out.print(formattedMessage);

            Files.writeString(
                logFilePath, 
                formattedMessage, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            addError("Помилка запису в файл логів", e);
        }
    }

    public void setLayout(ch.qos.logback.core.Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}

