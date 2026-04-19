package com.prodsearch.search.article;

/**
 * Типы распознанных артикулов.
 *
 * <ul>
 * <li>NUMERIC — только цифры, например: 0281002241</li>
 * <li>NUMERIC_COMPOSITE — цифры с разделителями между группами, например:
 * 0281-002-241</li>
 * <li>ALPHANUMERIC — смесь букв и цифр, например: W213, A2710900780</li>
 * </ul>
 */
public enum ArticleType {
    NUMERIC,
    NUMERIC_COMPOSITE,
    ALPHANUMERIC
}
