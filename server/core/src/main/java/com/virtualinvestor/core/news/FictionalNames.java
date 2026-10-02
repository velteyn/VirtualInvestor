package com.virtualinvestor.core.news;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guards the fiction: real companies and news outlets must never appear in generated news.
 * Detection is token-based (split on non-alphanumerics) so words like "metaverse" do not
 * trip the "meta" entry.
 */
public final class FictionalNames {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    private static final List<String> REAL_NAMES = List.of(
            "apple", "microsoft", "nvidia", "meta", "tesla", "amazon", "google", "alphabet",
            "netflix", "amd", "intel", "cisco", "paypal", "salesforce", "adobe", "oracle",
            "broadcom", "qualcomm", "uber", "lyft", "shopify", "square", "block", "coinbase",
            "snowflake", "palantir", "twitter", "spotify", "airbnb", "stripe",
            "reuters", "bloomberg", "cnbc", "wsj", "forbes", "yahoo");

    private FictionalNames() {
    }

    public static boolean containsRealName(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String[] tokens = TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT));
        for (String token : tokens) {
            if (REAL_NAMES.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
