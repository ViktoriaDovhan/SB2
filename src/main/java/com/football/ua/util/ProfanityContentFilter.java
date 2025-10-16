package com.football.ua.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProfanityContentFilter implements ContentFilter {
    private final Pattern pattern;

    public ProfanityContentFilter(Set<String> banned) {
        String alt = banned.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        String regex = "(?iu)(?<!\\p{L})(" + alt + ")(?!\\p{L})";
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String w = m.group(1);
            m.appendReplacement(sb, "*".repeat(w.length()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
