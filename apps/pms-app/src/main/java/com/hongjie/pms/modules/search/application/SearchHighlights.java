package com.hongjie.pms.modules.search.application;

import org.springframework.web.util.HtmlUtils;
import java.util.regex.Pattern;

public final class SearchHighlights {
    private SearchHighlights() {}

    public static String render(String text, String keyword, int limit) {
        if (text == null) return null;
        String value = text.length() > limit ? text.substring(0, limit) + "..." : text;
        if (keyword.isEmpty()) return HtmlUtils.htmlEscape(value);
        var matches =
                Pattern.compile(
                                Pattern.quote(keyword),
                                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(value);
        StringBuilder html = new StringBuilder();
        int start = 0;
        while (matches.find()) {
            html.append(HtmlUtils.htmlEscape(value.substring(start, matches.start())));
            html.append("<em class='highlight'>")
                    .append(HtmlUtils.htmlEscape(matches.group()))
                    .append("</em>");
            start = matches.end();
        }
        return html.append(HtmlUtils.htmlEscape(value.substring(start))).toString();
    }
}
