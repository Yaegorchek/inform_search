package com.prodsearch.search.article;

import java.util.Objects;

/**
 * Result of article recognition in text.
 * Stores both source representation and normalized/searchable representation.
 */
public final class ArticleMatch {

    private final String rawArticle;
    private final String normalizedArticle;
    private final String structuralMask;
    private final ArticleType articleType;

    public ArticleMatch(String rawArticle, String normalizedArticle, String structuralMask, ArticleType articleType) {
        this.rawArticle = rawArticle == null ? "" : rawArticle;
        this.normalizedArticle = normalizedArticle == null ? "" : normalizedArticle;
        this.structuralMask = structuralMask == null ? "" : structuralMask;
        this.articleType = Objects.requireNonNull(articleType, "articleType must not be null");
    }

    public String getRawArticle() {
        return rawArticle;
    }

    public String getNormalizedArticle() {
        return normalizedArticle;
    }

    public String getStructuralMask() {
        return structuralMask;
    }

    public ArticleType getArticleType() {
        return articleType;
    }

    public boolean isEmpty() {
        return normalizedArticle.isBlank();
    }

    /**
     * Utility score for choosing the best duplicate candidate.
     * Prefer longer raw representation when normalized forms collide.
     */
    public int rawLengthScore() {
        return rawArticle.length();
    }

    @Override
    public String toString() {
        return "ArticleMatch{" +
                "rawArticle='" + rawArticle + '\'' +
                ", normalizedArticle='" + normalizedArticle + '\'' +
                ", structuralMask='" + structuralMask + '\'' +
                ", articleType=" + articleType +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ArticleMatch that))
            return false;
        return Objects.equals(rawArticle, that.rawArticle)
                && Objects.equals(normalizedArticle, that.normalizedArticle)
                && Objects.equals(structuralMask, that.structuralMask)
                && articleType == that.articleType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawArticle, normalizedArticle, structuralMask, articleType);
    }
}
