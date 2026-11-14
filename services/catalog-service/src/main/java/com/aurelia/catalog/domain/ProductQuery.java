package com.aurelia.catalog.domain;

import java.util.Set;

public record ProductQuery(String collection, Set<String> colors, Set<String> fabrics, Long minPricePaise,
                           Long maxPricePaise, boolean inStockOnly, String text, Sort sort, int page, int size) {

    public enum Sort {
        FEATURED, NEWEST, PRICE_ASC, PRICE_DESC, NAME_ASC;

        public static Sort parse(String s) {
            if (s == null || s.isBlank()) return FEATURED;
            try {
                return valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return FEATURED;
            }
        }
    }
}
