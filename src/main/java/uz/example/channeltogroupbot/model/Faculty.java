package uz.example.channeltogroupbot.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Universitetdagi barcha fakultetlar ro'yxati.
 * <p>
 * Har bir fakultet uchun:
 * - {@code id}       - guruhni buyruq bilan biriktirishda ishlatiladigan raqam ({@code /fakultet 1})
 * - {@code displayName} - to'liq o'zbekcha nomi (xabarlarda ko'rsatiladi)
 * - {@code hashtag}  - kanal postida yozilishi kerak bo'lgan hashtag (masalan {@code #matematika})
 */
public enum Faculty {

    MATEMATIKA(1, "Matematika", "matematika"),
    AMALIY_MATEMATIKA(2, "Amaliy matematika va intelektual texnologiyalar", "amit"),
    FIZIKA(3, "Fizika", "fizika"),
    KIMYO(4, "Kimyo", "kimyo"),
    BIOLOGIYA(5, "Biologiya", "biologiya"),
    GEOLOGIYA(6, "Geologiya va muhandislik geologiyasi", "geologiya"),
    GEOGRAFIYA(7, "Geografiya va geoaxborot tizimlari", "geografiya"),
    IQTISODIYOT(8, "Iqtisodiyot", "iqtisodiyot"),
    TARIX(9, "Tarix", "tarix"),
    IJTIMOIY_FANLAR(10, "Ijtimoiy fanlar", "ijtimoiy"),
    XORIJIY_FILOLOGIYA(11, "Xorijiy filologiya", "filologiya"),
    SPORT(12, "Taekwondo va sport faoliyati", "sport"),
    JURNALISTIKA(13, "Jurnalistika va o'zbek filologiyasi", "jurnalistika");

    private final int id;
    private final String displayName;
    private final String hashtag;

    Faculty(int id, String displayName, String hashtag) {
        this.id = id;
        this.displayName = displayName;
        this.hashtag = hashtag;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Kanal postida qidiriladigan hashtag so'zi (# belgisisiz, kichik harflarda).
     */
    public String getHashtag() {
        return hashtag;
    }

    public static Optional<Faculty> byId(int id) {
        return Arrays.stream(values()).filter(f -> f.id == id).findFirst();
    }

    public static Optional<Faculty> byHashtag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        String normalized = tag.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.hashtag.equals(normalized)).findFirst();
    }

    /**
     * Foydalanuvchi {@code /fakultet} buyrug'iga yozgan matnni (raqam yoki
     * nomning bir qismi) fakultetga moslashtirishga harakat qiladi.
     */
    public static Optional<Faculty> byNumberOrName(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String trimmed = input.trim();

        try {
            int id = Integer.parseInt(trimmed);
            Optional<Faculty> byId = byId(id);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // raqam emas, nom bo'yicha qidiramiz
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(f -> f.displayName.toLowerCase(Locale.ROOT).contains(normalized)
                        || f.hashtag.equals(normalized))
                .findFirst();
    }

    /**
     * `/fakultetlar` buyrug'iga javob sifatida yuboriladigan to'liq ro'yxat matni.
     */
    public static String formattedList() {
        StringBuilder sb = new StringBuilder("Fakultetlar ro'yxati:\n\n");
        for (Faculty f : values()) {
            sb.append(f.id).append(". ").append(f.displayName)
                    .append("  ->  #").append(f.hashtag).append('\n');
        }
        sb.append("\nGuruhni fakultetga biriktirish uchun:\n/fakultet <raqam yoki nomi>\n")
                .append("Masalan: /fakultet 1  yoki  /fakultet Matematika");
        return sb.toString();
    }
}
