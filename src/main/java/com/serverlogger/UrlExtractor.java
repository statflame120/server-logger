package com.serverlogger;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public class UrlExtractor {

    // ── Patterns ──────────────────────────────────────────────────────────────

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://)?(?:www\\.)?([a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?" +
                    "(?:\\.[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?)+)" +
                    "(?::\\d{1,5})?(?:/[\\w\\-./?%&=]*)?",
            Pattern.CASE_INSENSITIVE
    );

    /** Only digits and dots — used to detect version strings vs. bare IPv4. */
    private static final Pattern DIGITS_AND_DOTS = Pattern.compile("^[\\d.]+$");

    /** Strict IPv4 — each octet 0-255, exactly three dots. */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    // ── Classification sets ───────────────────────────────────────────────────

    /** TLDs that indicate a real game-server domain. */
    private static final Set<String> GAME_TLDS = Set.of(
            ".com", ".net", ".org", ".gg", ".io", ".co", ".xyz", ".me", ".cc", ".us"
    );

    /**
     * Substrings that route a candidate to {@link CategorizedResult#detectedUrls}
     * instead of {@link CategorizedResult#gameAddresses}.
     * Checked after the scheme check so http:// addresses are already caught.
     */
    private static final Set<String> WEB_KEYWORDS = Set.of(
            "discord.gg", "discord.com",
            "google", "youtube", "twitch", "twitter", "reddit",
            "azure", "amazonaws", "cloudflare"
    );

    /** Hard-noise blacklist — these are never useful game addresses. */
    private static final Set<String> BLACKLIST = Set.of(
            "mojang.com", "minecraft.net", "microsoft.com", "localhost", "www.", "store."
    );

    /**
     * Hub / proxy networks whose addresses should be flagged as high-priority
     * for breadcrumb resolution.  Matched by substring so that subdomains like
     * {@code play.minehut.com} are caught by the {@code "minehut"} entry.
     */
    private static final Set<String> HIGH_PRIORITY_DOMAINS = Set.of(
            "minehut.gg", "minehut.com", "minehut",
            "fallentech.io", "mccentral.org"
    );

    // ── Result container ──────────────────────────────────────────────────────

    /**
     * Output of {@link #categorize}.
     *
     * <ul>
     *   <li>{@code gameAddresses}   — valid server addresses, sorted so named
     *       subdomains (play.*, mc.*, hub.*) appear before raw IPv4s.</li>
     *   <li>{@code detectedUrls}    — web links, Discord invites, cloud URLs.</li>
     *   <li>{@code versionStrings}  — digit-dot strings that look like software
     *       versions rather than addresses (e.g. {@code "1.21.1"}).</li>
     *   <li>{@code highPriorityMatches} — game addresses that match a known
     *       hub/proxy network and should be prioritised for breadcrumb lookup.</li>
     * </ul>
     */
    public record CategorizedResult(
            List<String> gameAddresses,
            List<String> detectedUrls,
            List<String> versionStrings,
            Set<String>  highPriorityMatches
    ) {
        /** True if any game address matched a high-priority hub network. */
        public boolean hasHighPriority() { return !highPriorityMatches.isEmpty(); }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Converts small-capital Unicode characters used in Minecraft server
     * branding to their standard ASCII equivalents, then applies NFKC
     * normalisation to collapse fullwidth / math-script variants.
     *
     * <p>Explicitly handled small caps (among others in the full map):
     * <ul>
     *   <li>U+0262 ɢ → g  (Small Capital G)</li>
     *   <li>U+1D00 ᴀ → a  (Small Capital A)</li>
     *   <li>U+1D07 ᴇ → e  (Small Capital E)</li>
     *   <li>U+0280 ʀ → r  (Small Capital R)</li>
     *   <li>U+1D0D ᴍ → m  (Small Capital M)</li>
     * </ul>
     */
    public static String normalize(String text) {
        if (text == null) return "";
        return transliterate(Normalizer.normalize(text, Normalizer.Form.NFKC));
    }

    /**
     * Extracts candidate addresses from {@code text} via three passes:
     *
     * <ol>
     *   <li>Raw regex on the original text.</li>
     *   <li>NFKC normalisation (fullwidth, math-script → ASCII).</li>
     *   <li>Homoglyph transliteration on top of the NFKC result (Cyrillic
     *       lookalikes and small-cap IPA letters).</li>
     * </ol>
     *
     * Falls back to the next pass only when the previous one finds nothing.
     */
    public static Set<String> extract(String text) {
        if (text == null || text.isBlank()) return Collections.emptySet();

        Set<String> results = extractRaw(text);
        if (!results.isEmpty()) return results;

        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        results = extractRaw(nfkc);
        if (!results.isEmpty()) return results;

        return extractRaw(transliterate(nfkc));
    }

    /**
     * Classifies a collection of already-extracted candidates into game
     * addresses, web URLs, and version strings, applying noise filtering,
     * normalisation, and high-priority detection.
     *
     * <p>Typical usage:
     * <pre>{@code
     *   Set<String> raw = UrlExtractor.extract(tabListText);
     *   CategorizedResult r = UrlExtractor.categorize(raw);
     * }</pre>
     */
    public static CategorizedResult categorize(Collection<String> candidates) {
        List<String> gameAddresses  = new ArrayList<>();
        List<String> detectedUrls   = new ArrayList<>();
        List<String> versionStrings = new ArrayList<>();
        Set<String>  highPriority   = new LinkedHashSet<>();

        for (String raw : candidates) {
            if (raw == null) continue;
            String candidate = normalize(raw).trim();
            if (candidate.isEmpty()) continue;

            String lower = candidate.toLowerCase(Locale.ROOT);

            // ── Version strings (digits + dots that are NOT valid IPv4) ────────
            if (DIGITS_AND_DOTS.matcher(lower).matches()) {
                int dots = (int) lower.chars().filter(c -> c == '.').count();
                // Exactly 3 dots and long enough → treat as IPv4, handled below.
                // Anything else (e.g. "1.21.1", "7.2.5") → version string.
                if (dots != 3 || lower.length() < 7) {
                    versionStrings.add(candidate);
                    continue;
                }
                // Falls through to game-address check as a bare IP.
            }

            // ── Hard noise ────────────────────────────────────────────────────
            if (isNoise(lower, false)) continue;

            // ── Web URLs: has scheme or matches a web-service keyword ─────────
            if (lower.contains("http") || WEB_KEYWORDS.stream().anyMatch(lower::contains)) {
                detectedUrls.add(candidate);
                continue;
            }

            // ── Game addresses: named domain with recognised TLD or bare IPv4 ─
            String bareHost = lower.split("[:/]")[0];
            if (hasGameTld(bareHost) || isValidIPv4(bareHost)) {
                gameAddresses.add(candidate);
                if (HIGH_PRIORITY_DOMAINS.stream().anyMatch(lower::contains)) {
                    highPriority.add(candidate);
                }
            }
            // Anything else lacks enough structure to be useful — discard.
        }

        // Sort: play.* → mc.* → hub.* → other named domains → raw IPv4s
        gameAddresses.sort(
                Comparator.comparingInt((String s) -> prefixRank(s.toLowerCase(Locale.ROOT)))
                          .thenComparing(Comparator.naturalOrder())
        );

        return new CategorizedResult(
                Collections.unmodifiableList(gameAddresses),
                Collections.unmodifiableList(detectedUrls),
                Collections.unmodifiableList(versionStrings),
                Collections.unmodifiableSet(highPriority)
        );
    }

    // ── Noise filter ──────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code candidate} is considered noise and should
     * be discarded entirely.
     *
     * <ul>
     *   <li>Shorter than 4 characters — too short to be a real address.</li>
     *   <li>Pure digits-and-dots that do not form a valid IPv4 — version
     *       numbers like {@code "7.2.5"} or {@code "1.21.1"}; these are
     *       collected separately by {@link #categorize} rather than discarded.</li>
     *   <li>Hard-blacklisted domains (mojang, minecraft.net, etc.).</li>
     * </ul>
     *
     * Package-private so callers in this package can reuse it.
     */
    static boolean isNoise(String candidate, boolean isTabList) {
        if (candidate == null || candidate.isBlank()) return true;
        String lower = candidate.toLowerCase(Locale.ROOT);

        // 1. Bedrock/Geyser usernames start with a single leading dot (.PlayerName)
        //    and have no further dots — real subdomains like .minehut.gg have at
        //    least one more dot after the prefix and must not be filtered.
        if (isTabList && lower.startsWith(".") && lower.indexOf('.', 1) < 0) return true;

        // 2. Too short to be meaningful.
        if (lower.length() < 4) return true;

        // 3. Digit-dot strings: version numbers handled separately; valid IPv4
        //    passes through (dots == 3 and length >= 7 checked by caller).
        if (DIGITS_AND_DOTS.matcher(lower).matches()) {
            int dots = (int) lower.chars().filter(c -> c == '.').count();
            return dots != 2;
        }

        // 4. Hard blacklist.
        return BLACKLIST.stream().anyMatch(lower::contains);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true if the bare host ends with a recognised game-server TLD. */
    private static boolean hasGameTld(String bareHost) {
        return GAME_TLDS.stream().anyMatch(bareHost::endsWith);
    }

    /** Strict IPv4 validation (no port, no path). */
    private static boolean isValidIPv4(String s) {
        if (s == null || s.length() < 7 || s.length() > 15) return false;
        return IPV4_PATTERN.matcher(s).matches();
    }

    /**
     * Sort rank for well-known subdomain prefixes.
     * Lower = earlier in the sorted list.
     */
    private static int prefixRank(String lower) {
        if (lower.startsWith("play.")) return 0;
        if (lower.startsWith("mc."))   return 1;
        if (lower.startsWith("hub."))  return 2;
        if (isValidIPv4(lower.split(":")[0])) return 99;
        return 50;
    }

    private static Set<String> extractRaw(String text) {
        Set<String> results = new LinkedHashSet<>();
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String match = m.group().trim();
            if (!isNoise(match, false)) results.add(match);
        }
        return results;
    }

    // ── Homoglyph transliteration ─────────────────────────────────────────────

    /**
     * Maps Unicode characters that NFKC does not normalise — Cyrillic
     * lookalikes, IPA small-cap letters, and Unicode dot variants — to their
     * closest ASCII equivalents.
     *
     * <p>Small-cap letters explicitly requested by the caller are marked *.
     */
    private static final Map<Character, Character> HOMOGLYPHS;
    static {

        // Cyrillic lowercase lookalikes

        // fullwidth full stop ．
        HOMOGLYPHS = Map.ofEntries(Map.entry('а', 'a'), Map.entry('е', 'e'), Map.entry('о', 'o'), Map.entry('р', 'p'), Map.entry('с', 'c'), Map.entry('х', 'x'), Map.entry('у', 'y'), Map.entry('і', 'i'), Map.entry('ѕ', 's'),

                // Cyrillic uppercase lookalikes
                Map.entry('А', 'A'), Map.entry('В', 'B'), Map.entry('Е', 'E'), Map.entry('К', 'K'), Map.entry('М', 'M'), Map.entry('Н', 'H'), Map.entry('О', 'O'), Map.entry('Р', 'P'), Map.entry('С', 'C'), Map.entry('Т', 'T'), Map.entry('Х', 'X'), Map.entry('І', 'I'),

                // IPA / small-capital letters
                Map.entry('ᴀ', 'a'), // U+1D00  Small Capital A  *
                Map.entry('ʙ', 'b'), // U+0299  Small Capital B
                Map.entry('ᴄ', 'c'), // U+1D04  Small Capital C
                Map.entry('ᴅ', 'd'), // U+1D05  Small Capital D
                Map.entry('ᴇ', 'e'), // U+1D07  Small Capital E  *
                Map.entry('ɢ', 'g'), // U+0262  Small Capital G  *
                Map.entry('ʜ', 'h'), // U+029C  Small Capital H
                Map.entry('ɪ', 'i'), // U+026A  Small Capital I
                Map.entry('ᴊ', 'j'), // U+1D0A  Small Capital J
                Map.entry('ᴋ', 'k'), // U+1D0B  Small Capital K
                Map.entry('ʟ', 'l'), // U+029F  Small Capital L
                Map.entry('ᴍ', 'm'), // U+1D0D  Small Capital M  *
                Map.entry('ɴ', 'n'), // U+0274  Small Capital N
                Map.entry('ᴏ', 'o'), // U+1D0F  Small Capital O
                Map.entry('ᴘ', 'p'), // U+1D18  Small Capital P
                Map.entry('ʀ', 'r'), // U+0280  Small Capital R  *
                Map.entry('ᴛ', 't'), // U+1D1B  Small Capital T
                Map.entry('ᴜ', 'u'), // U+1D1C  Small Capital U
                Map.entry('ᴠ', 'v'), // U+1D20  Small Capital V
                Map.entry('ᴡ', 'w'), // U+1D21  Small Capital W
                Map.entry('ʏ', 'y'), // U+028F  Small Capital Y
                Map.entry('ᴢ', 'z'), // U+1D22  Small Capital Z

                // Unicode dot / period alternatives
                Map.entry('·', '.'), // middle dot        ·
                Map.entry('•', '.'), // bullet             •
                Map.entry('․', '.'), // one dot leader     ․
                Map.entry('．', '.'));
    }

    private static String transliterate(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(HOMOGLYPHS.getOrDefault(c, c));
        }
        return sb.toString();
    }
}
