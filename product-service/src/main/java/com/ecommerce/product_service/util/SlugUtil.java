package com.ecommerce.product_service.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utility class for generating URL-friendly slugs from strings.
 */
public class SlugUtil {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-+");

    /**
     * Generates a URL-friendly slug from the given input string.
     *
     * Rules:
     * - Converts to lowercase
     * - Removes accents and diacritics
     * - Replaces whitespace with hyphens
     * - Removes special characters
     * - Trims multiple consecutive hyphens
     *
     * Example: "Home & Garden" -> "home-and-garden"
     *
     * @param input the string to convert to a slug
     * @return the generated slug, or empty string if input is null/blank
     */
    public static String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = input.trim();

        // Normalize to decomposed form (NFD) and remove diacritics
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Convert to lowercase
        normalized = normalized.toLowerCase(Locale.ROOT);

        // Replace whitespace with hyphens
        normalized = WHITESPACE.matcher(normalized).replaceAll("-");

        // Remove non-latin characters (keep only alphanumeric and hyphens)
        normalized = NON_LATIN.matcher(normalized).replaceAll("");

        // Replace multiple consecutive hyphens with a single hyphen
        normalized = MULTIPLE_HYPHENS.matcher(normalized).replaceAll("-");

        // Trim leading and trailing hyphens
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");

        return normalized;
    }

    /**
     * Generates a unique slug by appending a counter if the base slug already exists.
     *
     * @param baseSlug the base slug to use
     * @param existsChecker function that checks if a slug already exists
     * @return a unique slug
     */
    public static String generateUniqueSlug(String baseSlug, SlugExistsChecker existsChecker) {
        if (baseSlug == null || baseSlug.isBlank()) {
            return "";
        }

        String slug = baseSlug;
        int counter = 1;

        while (existsChecker.exists(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    /**
     * Functional interface for checking if a slug exists.
     */
    @FunctionalInterface
    public interface SlugExistsChecker {
        boolean exists(String slug);
    }
}
