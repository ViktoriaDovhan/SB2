package com.football.ua.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CustomLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                        .withZone(ZoneId.systemDefault());

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[")
          .append(DATE_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())))
          .append("] | ");
        
        sb.append(String.format("%-5s", event.getLevel()))
          .append(" | ");
        
        if (event.getMarker() != null) {
            sb.append("[MARKER: ")
              .append(event.getMarker().getName())
              .append("] | ");
        }
        
        String loggerName = event.getLoggerName();
        String shortName = loggerName.substring(loggerName.lastIndexOf('.') + 1);
        sb.append(String.format("%-30s", shortName))
          .append(" | ");
        
        java.util.Map<String, String> mdc = event.getMDCPropertyMap();
        if (!mdc.isEmpty()) {
            sb.append("[MDC: ");
            mdc.forEach((key, value) -> sb.append(key).append("=").append(value).append(", "));
            sb.delete(sb.length() - 2, sb.length());
            sb.append("] | ");
        }
        
        sb.append(event.getFormattedMessage());
        
        if (event.getThrowableProxy() != null) {
            sb.append("\n")
              .append("Exception: ")
              .append(event.getThrowableProxy().getClassName())
              .append(": ")
              .append(event.getThrowableProxy().getMessage());
        }
        
        sb.append("\n");
        
        return sb.toString();
    }
}

