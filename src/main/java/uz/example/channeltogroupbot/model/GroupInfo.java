package uz.example.channeltogroupbot.model;

/**
 * Ro'yxatdagi bitta chat (guruh YOKI kanal) haqidagi ma'lumot.
 * Jackson orqali {@code groups.json} fayliga saqlash/o'qish uchun oddiy POJO.
 */
public class GroupInfo {

    private Long chatId;
    private String title;

    /**
     * Chatning Telegram username'i (masalan "mexmat_kanali", @ belgisisiz).
     * Shaxsiy chatda {@code /fakultet @username ...} buyrug'i orqali
     * topish uchun ishlatiladi. Username bo'lmasa (masalan yopiq guruh) - {@code null}.
     */
    private String username;

    /**
     * Chat turi: "group", "supergroup" yoki "channel".
     */
    private String chatType;

    /**
     * Guruh/kanal biriktirilgan fakultet ID si ({@link Faculty#getId()}).
     * Hali hech qanday fakultetga biriktirilmagan bo'lsa - {@code null}.
     */
    private Integer facultyId;

    /**
     * Jackson uchun bo'sh konstruktor (JSON'dan o'qishda kerak).
     */
    public GroupInfo() {
    }

    public GroupInfo(Long chatId, String title, String username, String chatType, Integer facultyId) {
        this.chatId = chatId;
        this.title = title;
        this.username = username;
        this.chatType = chatType;
        this.facultyId = facultyId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public Integer getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(Integer facultyId) {
        this.facultyId = facultyId;
    }
}

