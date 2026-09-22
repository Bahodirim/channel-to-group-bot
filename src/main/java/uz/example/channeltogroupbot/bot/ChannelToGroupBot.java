package uz.example.channeltogroupbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.example.channeltogroupbot.model.Faculty;
import uz.example.channeltogroupbot.model.GroupInfo;
import uz.example.channeltogroupbot.service.GroupStorageService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Botning asosiy klassi (V2 — fakultet bo'yicha yo'naltirish).
 * <p>
 * Asosiy vazifalar:
 * <ol>
 *     <li>Bot biror guruhda ADMIN qilib tayinlanganda, o'sha guruh avtomatik
 *     aniqlanib ro'yxatga qo'shiladi (hali fakultetsiz holatda).</li>
 *     <li>Guruh admini {@code /fakultet <raqam yoki nomi>} buyrug'i orqali
 *     o'sha guruhni biror fakultetga biriktiradi.</li>
 *     <li>Kuzatilayotgan kanalga post joylanganda, undagi hashtag(lar)
 *     (masalan {@code #matematika}) orqali qaysi fakultet(lar)ga tegishli
 *     ekanligi aniqlanadi va post FAQAT o'sha fakultet(lar)ga biriktirilgan
 *     guruhlarga jo'natiladi. {@code #barchasi} hashtagi barcha guruhlarga
 *     yuborishni bildiradi.</li>
 * </ol>
 */
@Component
public class ChannelToGroupBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(ChannelToGroupBot.class);

    private static final String STATUS_ADMINISTRATOR = "administrator";
    private static final String STATUS_CREATOR = "creator";
    private static final String CHAT_TYPE_GROUP = "group";
    private static final String CHAT_TYPE_SUPERGROUP = "supergroup";
    private static final String CHAT_TYPE_CHANNEL = "channel";
    private static final String CHAT_TYPE_PRIVATE = "private";

    /**
     * Postlardagi hashtaglarni ushlab olish uchun (masalan #matematika, #Fizika).
     */
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_]+)", Pattern.UNICODE_CASE);

    /**
     * Ushbu hashtaglardan biri postda bo'lsa, u BARCHA guruhlarga jo'natiladi.
     */
    private static final Set<String> BROADCAST_TAGS = Set.of("barchasi", "hammasi", "barcha");

    /**
     * Bitta xabarni ikki marta (masalan, avval "channel_post", keyin uning
     * caption biriktirilgan "edited_channel_post" varianti orqali) qayta
     * jo'natib yubormaslik uchun oxirgi jo'natilgan postlarni eslab turadi.
     */
    private static final int MAX_TRACKED_POSTS = 1000;
    private final Set<String> recentlyForwardedPosts = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_TRACKED_POSTS;
                }
            }));

    private final String botUsername;
    private final String channelId;
    private final GroupStorageService groupStorageService;

    public ChannelToGroupBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.channel.id}") String channelId,
            GroupStorageService groupStorageService) {
        super(botToken);
        this.botUsername = botUsername;
        this.channelId = channelId;
        this.groupStorageService = groupStorageService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            log.info("[UPDATE] id={}, my_chat_member={}, channel_post={}, edited_channel_post={}, message={}",
                    update.getUpdateId(), update.hasMyChatMember(), update.hasChannelPost(),
                    update.hasEditedChannelPost(), update.hasMessage());

            // 1) Bot biror guruhda admin/a'zo/chiqarilgan holatiga o'zgarganda keladi
            if (update.hasMyChatMember()) {
                handleMyChatMember(update.getMyChatMember());
                return;
            }

            // 2) Guruh/shaxsiy chatda yuborilgan buyruqlar (/fakultet, /fakultetlar va h.k.)
            if (update.hasMessage() && update.getMessage().isCommand()) {
                handleCommand(update.getMessage());
                return;
            }

            // 3) Kuzatilayotgan kanalga yangi post joylanganda keladi
            if (update.hasChannelPost()) {
                handleChannelPost(update.getChannelPost(), false);
                return;
            }

            // 4) Rasm/video + matn (caption) bilan post yuborilganda, Telegram
            // ko'pincha uni avval oddiy channel_post sifatida emas, balki bir
            // zumda "tahrirlangan" (edited_channel_post) sifatida yuboradi -
            // shu sababli buni ham kuzatamiz.
            if (update.hasEditedChannelPost()) {
                handleChannelPost(update.getEditedChannelPost(), true);
            }
        } catch (Exception e) {
            log.error("Update'ni qayta ishlashda kutilmagan xatolik: {}", e.getMessage(), e);
        }
    }

    // ==================== GURUHNI ANIQLASH ====================

    /**
     * Bot biror chatda (guruh YOKI kanal) ADMINISTRATOR qilib tayinlanganda
     * o'sha chatni avtomatik ravishda ro'yxatga qo'shadi (hali fakultetsiz).
     * Agar bot chatdan chiqarilsa yoki adminlikdan tushirilsa, ro'yxatdan olib tashlaydi.
     */
    private void handleMyChatMember(ChatMemberUpdated chatMemberUpdated) {
        Chat chat = chatMemberUpdated.getChat();
        String chatType = chat.getType();

        boolean isRelevantType = CHAT_TYPE_GROUP.equals(chatType) || CHAT_TYPE_SUPERGROUP.equals(chatType)
                || CHAT_TYPE_CHANNEL.equals(chatType);
        if (!isRelevantType) {
            return;
        }

        ChatMember newChatMember = chatMemberUpdated.getNewChatMember();
        ChatMember oldChatMember = chatMemberUpdated.getOldChatMember();
        String newStatus = newChatMember.getStatus();
        String oldStatus = oldChatMember != null ? oldChatMember.getStatus() : "noma'lum";

        log.info("Chatda bot holati o'zgardi -> \"{}\" (ID: {}, turi: {}), eski holat: {}, yangi holat: {}",
                chat.getTitle(), chat.getId(), chatType, oldStatus, newStatus);

        if (STATUS_ADMINISTRATOR.equals(newStatus)) {
            boolean isNew = groupStorageService.registerGroup(chat.getId(), chat.getTitle(), chat.getUserName(), chatType);
            if (isNew && (CHAT_TYPE_GROUP.equals(chatType) || CHAT_TYPE_SUPERGROUP.equals(chatType))) {
                // Guruhlarda bot xabar yozishi tabiiy, shuning uchun yo'riqnomani
                // to'g'ridan-to'g'ri o'sha yerga yuboramiz.
                sendText(chat.getId(), "Salom! Men shu guruhda admin qilib tayinlandim ✅\n\n"
                        + "Endi ushbu guruhni biror fakultetga biriktiring, shunda kanaldagi shu fakultetga oid "
                        + "postlar avtomatik shu yerga kelib turadi:\n\n/fakultet <raqam yoki nomi>\n\n"
                        + Faculty.formattedList());
            } else if (isNew) {
                // Kanallarga esa botning "texnik" xabarlarini yubormaymiz - rasmiy
                // kanalni bo'lar-bo'lmasga to'ldirmaslik uchun. Buning o'rniga
                // adminga shaxsiy chatda /fakultet buyrug'idan foydalanishni so'raymiz.
                log.info("Kanal ro'yxatga qo'shildi. Uni fakultetga biriktirish uchun admin botga SHAXSIY "
                        + "chatda quyidagini yuborishi kerak: /fakultet {} <fakultet raqami yoki nomi>",
                        chat.getUserName() != null ? "@" + chat.getUserName() : chat.getId());
            }
        } else {
            groupStorageService.removeGroup(chat.getId());
        }
    }

    // ==================== BUYRUQLAR ====================

    private void handleCommand(Message message) {
        Chat chat = message.getChat();
        String text = message.getText() == null ? "" : message.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        String[] parts = text.split("\\s+", 2);
        // "/fakultet@BotUsername" ko'rinishidagi buyruqlarni ham qo'llab-quvvatlaymiz
        String command = parts[0].split("@")[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "/start" -> handleStartCommand(chat);
            case "/fakultetlar" -> sendText(chat.getId(), Faculty.formattedList());
            case "/fakultet" -> handleFacultyAssignCommand(message, chat, args);
            case "/holat" -> handleStatusCommand(chat, args);
            case "/kanallarim" -> handleMyChannelsCommand(message, chat);
            default -> {
                // noma'lum buyruq - e'tiborsiz qoldiramiz
            }
        }
    }

    private void handleStartCommand(Chat chat) {
        if (CHAT_TYPE_PRIVATE.equals(chat.getType())) {
            sendText(chat.getId(), "Salom! Men kanal postlarini fakultet kanallariga/guruhlariga taqsimlovchi botman.\n\n"
                    + "GURUH uchun: botni guruhga qo'shib ADMIN qiling - men avtomatik ro'yxatga qo'shilaman, "
                    + "so'ng o'sha guruhda /fakultet <raqam> buyrug'ini yuboring.\n\n"
                    + "KANAL uchun: botni kanalga qo'shib ADMIN qiling, so'ng SHU YERDA (shaxsiy chatda) "
                    + "quyidagicha yozing:\n/fakultet @kanal_username <raqam>\n\n"
                    + "O'zingiz admin bo'lgan, ro'yxatdagi kanal/guruhlarni ko'rish uchun: /kanallarim\n\n"
                    + Faculty.formattedList());
            return;
        }
        sendText(chat.getId(), "Salom! Ushbu guruhni fakultetga biriktirish uchun:\n/fakultet <raqam yoki nomi>\n\n"
                + Faculty.formattedList());
    }

    /**
     * {@code /fakultet ...} buyrug'ini qayta ishlaydi. Ikki xil rejimda ishlaydi:
     * <ul>
     *     <li>GURUHDA yuborilsa: {@code /fakultet <raqam yoki nomi>} - joriy
     *     guruhning o'zini biriktiradi (yuboruvchi shu guruh admini bo'lishi kerak).</li>
     *     <li>SHAXSIY chatda yuborilsa: {@code /fakultet <@username yoki ID> <raqam yoki nomi>}
     *     - ko'rsatilgan kanal/guruhni biriktiradi (yuboruvchi o'sha chatning
     *     admini bo'lishi kerak - bu {@code GetChatMember} orqali tekshiriladi).
     *     Kanallar uchun FAQAT shu usul ishlaydi, chunki kanal postida
     *     yuboruvchining shaxsini aniqlab bo'lmaydi.</li>
     * </ul>
     */
    private void handleFacultyAssignCommand(Message message, Chat chat, String args) {
        User sender = message.getFrom();
        if (sender == null) {
            sendText(chat.getId(), "Yuboruvchini aniqlab bo'lmadi.");
            return;
        }

        Long targetChatId;
        String facultyArg;

        if (CHAT_TYPE_PRIVATE.equals(chat.getType())) {
            // Shaxsiy chat: /fakultet <@username yoki ID> <raqam yoki nomi>
            String[] parts = args.split("\\s+", 2);
            if (parts.length < 2 || parts[0].isBlank()) {
                sendText(chat.getId(), "Shaxsiy chatda ishlatish uchun:\n/fakultet <@kanal_username yoki ID> <fakultet raqami yoki nomi>\n\n"
                        + "Masalan: /fakultet @mexmat_kanali 1\n\n"
                        + "Qaysi kanal/guruhlarga adminlik huquqingiz borligini ko'rish uchun: /kanallarim");
                return;
            }
            targetChatId = groupStorageService.resolveChatId(parts[0]);
            facultyArg = parts[1].trim();

            if (targetChatId == null) {
                sendText(chat.getId(), "\"" + parts[0] + "\" nomli kanal/guruh ro'yxatda topilmadi. "
                        + "Avval botni o'sha kanal/guruhda ADMIN qilib qo'shganingizga ishonch hosil qiling "
                        + "(agar username o'zgargan bo'lsa, raqamli ID orqali urinib ko'ring).");
                return;
            }
        } else if (CHAT_TYPE_GROUP.equals(chat.getType()) || CHAT_TYPE_SUPERGROUP.equals(chat.getType())) {
            // Guruh ichida: /fakultet <raqam yoki nomi> - joriy guruhning o'zi
            targetChatId = chat.getId();
            facultyArg = args;

            if (groupStorageService.getGroup(targetChatId) == null) {
                sendText(chat.getId(), "Bu guruh hali ro'yxatda emas. Avval botni ushbu guruhda ADMIN qiling.");
                return;
            }
            if (facultyArg.isBlank()) {
                sendText(chat.getId(), "Fakultetni ko'rsating. Masalan: /fakultet 1\n\n" + Faculty.formattedList());
                return;
            }
        } else {
            // Kanal ichidan buyruq amalda kelmaydi (channel_post orqali keladi,
            // hasMessage() orqali emas), lekin ehtiyot uchun rad javobi qoldiramiz.
            sendText(chat.getId(), "Bu buyruq bu yerda ishlamaydi. Kanalni biriktirish uchun botga "
                    + "SHAXSIY chatda yozing: /fakultet @kanal_username <raqam>");
            return;
        }

        if (!isChatAdmin(targetChatId, sender.getId())) {
            sendText(chat.getId(), "❌ Faqat shu kanal/guruhning adminlari fakultetni belgilashi mumkin.");
            return;
        }

        Optional<Faculty> facultyOpt = Faculty.byNumberOrName(facultyArg);
        if (facultyOpt.isEmpty()) {
            sendText(chat.getId(), "Bunday fakultet topilmadi. To'g'ri ro'yxat:\n\n" + Faculty.formattedList());
            return;
        }

        Faculty faculty = facultyOpt.get();
        groupStorageService.assignFaculty(targetChatId, faculty.getId());

        GroupInfo target = groupStorageService.getGroup(targetChatId);
        String targetLabel = target != null && target.getTitle() != null ? "\"" + target.getTitle() + "\"" : String.valueOf(targetChatId);
        sendText(chat.getId(), "✅ " + targetLabel + " endi \"" + faculty.getDisplayName() + "\" fakultetiga biriktirildi.\n"
                + "Kanalda #" + faculty.getHashtag() + " hashtagi bilan joylangan postlar endi shu yerga keladi.");
    }

    /**
     * {@code /holat} buyrug'ini qayta ishlaydi. Guruhda argumentsiz joriy
     * guruhning holatini, shaxsiy chatda esa {@code /holat <@username yoki ID>}
     * ko'rinishida ko'rsatilgan kanal/guruh holatini ko'rsatadi.
     */
    private void handleStatusCommand(Chat chat, String args) {
        Long targetChatId;
        if (CHAT_TYPE_PRIVATE.equals(chat.getType())) {
            if (args.isBlank()) {
                sendText(chat.getId(), "Shaxsiy chatda ishlatish uchun: /holat <@kanal_username yoki ID>");
                return;
            }
            targetChatId = groupStorageService.resolveChatId(args.trim());
            if (targetChatId == null) {
                sendText(chat.getId(), "\"" + args.trim() + "\" ro'yxatda topilmadi.");
                return;
            }
        } else {
            targetChatId = chat.getId();
        }

        GroupInfo info = groupStorageService.getGroup(targetChatId);
        if (info == null) {
            sendText(chat.getId(), "Bu chat hali ro'yxatda emas. Botni ADMIN qiling.");
            return;
        }
        String facultyText = info.getFacultyId() == null
                ? "hali biriktirilmagan. /fakultet <raqam> orqali belgilang."
                : Faculty.byId(info.getFacultyId()).map(f -> "\"" + f.getDisplayName() + "\" (#" + f.getHashtag() + ")")
                        .orElse("noma'lum");
        sendText(chat.getId(), "\"" + info.getTitle() + "\" joriy fakulteti: " + facultyText);
    }

    /**
     * {@code /kanallarim} buyrug'i (faqat shaxsiy chatda) - yuboruvchi admin
     * bo'lgan va ro'yxatdagi barcha kanal/guruhlarni topib ko'rsatadi. Bu
     * kanal/guruhning aniq ID/username'ini eslab yurishga hojat qoldirmaydi.
     */
    private void handleMyChannelsCommand(Message message, Chat chat) {
        if (!CHAT_TYPE_PRIVATE.equals(chat.getType())) {
            sendText(chat.getId(), "Bu buyruq faqat botning shaxsiy chatida ishlaydi.");
            return;
        }
        User sender = message.getFrom();
        if (sender == null) {
            return;
        }

        List<GroupInfo> mine = new ArrayList<>();
        for (GroupInfo info : groupStorageService.getAllGroups()) {
            if (isChatAdmin(info.getChatId(), sender.getId())) {
                mine.add(info);
            }
        }

        if (mine.isEmpty()) {
            sendText(chat.getId(), "Siz admin bo'lgan va ro'yxatda mavjud kanal/guruh topilmadi. "
                    + "Avval botni kerakli kanal/guruhga ADMIN qilib qo'shing.");
            return;
        }

        StringBuilder sb = new StringBuilder("Sizga tegishli kanal/guruhlar:\n\n");
        for (GroupInfo info : mine) {
            String facultyText = info.getFacultyId() == null
                    ? "biriktirilmagan"
                    : Faculty.byId(info.getFacultyId()).map(Faculty::getDisplayName).orElse("noma'lum");
            String ref = info.getUsername() != null ? "@" + info.getUsername() : String.valueOf(info.getChatId());
            sb.append("• \"").append(info.getTitle()).append("\" (").append(ref).append(") - ")
                    .append(facultyText).append('\n');
        }
        sb.append("\nBiriktirish uchun: /fakultet <@username yoki ID> <fakultet raqami>");
        sendText(chat.getId(), sb.toString());
    }

    /**
     * Berilgan foydalanuvchi ko'rsatilgan chatda (guruh yoki kanal) admin
     * (yoki egasi) ekanligini Telegram Bot API orqali tekshiradi.
     */
    private boolean isChatAdmin(Long chatId, Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId);
            getChatMember.setUserId(userId);
            ChatMember member = execute(getChatMember);
            String status = member.getStatus();
            return STATUS_ADMINISTRATOR.equals(status) || STATUS_CREATOR.equals(status);
        } catch (TelegramApiException e) {
            log.error("Foydalanuvchi ({}) chatda ({}) admin ekanligini tekshirishda xatolik: {}",
                    userId, chatId, e.getMessage());
            return false;
        }
    }

    // ==================== KANAL POSTLARI ====================

    /**
     * Kuzatilayotgan kanaldan yangi (yoki caption biriktirilgan) post kelganda,
     * undagi hashtaglar orqali aniqlangan fakultet guruh(lar)iga jo'natadi.
     *
     * @param isEdit true bo'lsa, bu post "edited_channel_post" orqali kelgan
     */
    private void handleChannelPost(Message channelPost, boolean isEdit) {
        Chat chat = channelPost.getChat();

        log.info("Kelgan post: kanal_id={}, kanal_username={}, message_id={}, isEdit={}, mazmuni={}",
                chat.getId(), chat.getUserName(), channelPost.getMessageId(), isEdit, describeContent(channelPost));

        if (!isTargetChannel(chat)) {
            log.warn("DIQQAT: kuzatilmayotgan kanaldan post keldi -> kelgan kanal ID: {}, kelgan username: {} | "
                            + "sizning application.properties'dagi telegram.channel.id qiymatingiz: \"{}\". "
                            + "Bu ikkisi mos kelishi kerak!",
                    chat.getId(), chat.getUserName(), channelId);
            return;
        }

        String postKey = chat.getId() + ":" + channelPost.getMessageId();
        if (!recentlyForwardedPosts.add(postKey)) {
            log.debug("Bu post ({}) allaqachon jo'natilgan, qayta jo'natilmaydi.", postKey);
            return;
        }

        Set<String> hashtags = extractHashtags(channelPost);
        List<Long> targetGroupIds = resolveTargetGroups(hashtags);

        log.info("Kanalda post aniqlandi (message_id={}, edit={}, mazmun={}, hashtaglar={}). {} ta guruhga jo'natilmoqda...",
                channelPost.getMessageId(), isEdit, describeContent(channelPost), hashtags, targetGroupIds.size());

        if (targetGroupIds.isEmpty()) {
            log.warn("Ushbu post hech qanday fakultet guruhiga mos kelmadi (hashtag topilmadi yoki shu fakultetga "
                    + "biriktirilgan guruh yo'q), shuning uchun hech kimga yuborilmadi. Postga #fakultetnomi "
                    + "(masalan #matematika) yoki barcha guruhlar uchun #barchasi hashtagini qo'shing.");
            return;
        }

        for (Long groupId : targetGroupIds) {
            sendCopyToGroup(channelPost, groupId);
        }
    }

    /**
     * Post matni/caption'idagi barcha hashtaglarni (kichik harflarda, # belgisisiz) ajratib oladi.
     */
    private Set<String> extractHashtags(Message message) {
        String content = message.getCaption() != null ? message.getCaption()
                : (message.getText() != null ? message.getText() : "");
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    /**
     * Aniqlangan hashtaglar asosida postni qaysi guruhlarga jo'natish kerakligini hisoblaydi.
     */
    private List<Long> resolveTargetGroups(Set<String> hashtags) {
        boolean broadcast = hashtags.stream().anyMatch(BROADCAST_TAGS::contains);
        if (broadcast) {
            return groupStorageService.getAllGroupIds();
        }

        Set<Long> result = new LinkedHashSet<>();
        for (String tag : hashtags) {
            Faculty.byHashtag(tag).ifPresent(faculty ->
                    result.addAll(groupStorageService.getGroupIdsByFaculty(faculty.getId())));
        }
        return new ArrayList<>(result);
    }

    /**
     * Log'larda muammoni tezroq aniqlash uchun postning mazmun turini
     * (matn, rasm, video va h.k.) qisqacha tavsiflaydi.
     */
    private String describeContent(Message message) {
        if (message.hasPhoto()) {
            return "rasm" + (message.getCaption() != null ? "+caption" : "");
        }
        if (message.hasVideo()) {
            return "video" + (message.getCaption() != null ? "+caption" : "");
        }
        if (message.hasDocument()) {
            return "fayl/dokument" + (message.getCaption() != null ? "+caption" : "");
        }
        if (message.hasText()) {
            return "matn";
        }
        return "boshqa";
    }

    /**
     * Kelgan post `telegram.channel.id` konfiguratsiyasida ko'rsatilgan
     * kanaldan ekanligini tekshiradi (username yoki raqamli ID bo'yicha).
     */
    private boolean isTargetChannel(Chat chat) {
        String usernameWithAt = chat.getUserName() != null ? "@" + chat.getUserName() : null;
        return channelId.equals(usernameWithAt) || channelId.equals(String.valueOf(chat.getId()));
    }

    private void sendCopyToGroup(Message channelPost, Long groupId) {
        try {
            CopyMessage copyMessage = new CopyMessage();
            copyMessage.setChatId(String.valueOf(groupId));
            copyMessage.setFromChatId(String.valueOf(channelPost.getChatId()));
            copyMessage.setMessageId(channelPost.getMessageId());
            execute(copyMessage);
            log.info("Post muvaffaqiyatli jo'natildi -> Guruh ID: {}", groupId);
        } catch (TelegramApiException e) {
            // To'liq xatolik matnini chiqaramiz - masalan, agar kanalda
            // "content protection" (himoyalangan kontent) yoqilgan bo'lsa,
            // Telegram bunday xabarlarni copyMessage orqali nusxalashga
            // ruxsat bermaydi va bu yerda aniq sabab bilan xato ko'rinadi.
            log.error("Post guruhga jo'natishda xatolik yuz berdi (Guruh ID: {}): {}", groupId, e.getMessage(), e);
        }
    }

    private void sendText(Long chatId, String text) {
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setText(text);
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Xabar yuborishda xatolik (chatId={}): {}", chatId, e.getMessage(), e);
        }
    }
}
