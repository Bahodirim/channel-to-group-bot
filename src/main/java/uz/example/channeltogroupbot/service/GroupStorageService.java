package uz.example.channeltogroupbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.example.channeltogroupbot.model.Faculty;
import uz.example.channeltogroupbot.model.GroupInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Bot admin qilib qo'shilgan guruhlarni va ularning qaysi fakultetga
 * biriktirilganini boshqaradi.
 * <p>
 * - Ma'lumotlar xotirada {@code Map<chatId, GroupInfo>} ko'rinishida turadi.
 * - Dastur qayta ishga tushirilganda yo'qolib qolmasligi uchun JSON faylga
 * (groups.json) yozib boriladi va ilova ishga tushganda o'sha fayldan
 * o'qib olinadi.
 */
@Service
public class GroupStorageService {

    private static final Logger log = LoggerFactory.getLogger(GroupStorageService.class);

    private final Map<Long, GroupInfo> groups = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File storageFile;

    public GroupStorageService(@Value("${telegram.storage.file:groups.json}") String storageFilePath) {
        this.storageFile = new File(storageFilePath);
        loadFromFile();
    }

    /**
     * Bot yangi chatda (guruh yoki kanal) admin qilinganda chaqiriladi. Agar
     * chat avvaldan ro'yxatda bo'lmasa, uni fakultetsiz (facultyId = null)
     * qo'shadi. Agar allaqachon ro'yxatda bo'lsa, faqat nomi/username'ini yangilaydi.
     *
     * @return true - agar chat yangi qo'shilgan bo'lsa
     */
    public synchronized boolean registerGroup(Long chatId, String title, String username, String chatType) {
        GroupInfo existing = groups.get(chatId);
        if (existing != null) {
            existing.setTitle(title);
            existing.setUsername(username);
            existing.setChatType(chatType);
            saveToFile();
            return false;
        }
        groups.put(chatId, new GroupInfo(chatId, title, username, chatType, null));
        saveToFile();
        log.info(">>> Yangi chat ro'yxatga qo'shildi. ID: {}, nomi: \"{}\", username: {}, turi: {} (hali fakultet biriktirilmagan)",
                chatId, title, username, chatType);
        printAllGroups();
        return true;
    }

    /**
     * {@code @username} yoki raqamli ID ko'rinishidagi matnni ro'yxatdagi
     * mos chat ID siga aylantiradi. Topilmasa {@code null} qaytaradi.
     */
    public Long resolveChatId(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String trimmed = ref.trim();
        if (trimmed.startsWith("@")) {
            String uname = trimmed.substring(1).toLowerCase(Locale.ROOT);
            return groups.values().stream()
                    .filter(g -> g.getUsername() != null && g.getUsername().equalsIgnoreCase(uname))
                    .map(GroupInfo::getChatId)
                    .findFirst()
                    .orElse(null);
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Guruhni butunlay ro'yxatdan o'chiradi (bot guruhdan chiqarilganda yoki
     * adminlikdan tushirilganda chaqiriladi).
     */
    public synchronized boolean removeGroup(Long chatId) {
        GroupInfo removed = groups.remove(chatId);
        if (removed != null) {
            saveToFile();
            log.info(">>> Guruh ro'yxatdan o'chirildi. Guruh ID: {}", chatId);
            printAllGroups();
        }
        return removed != null;
    }

    /**
     * Guruhni ko'rsatilgan fakultetga biriktiradi.
     *
     * @return true - agar guruh ro'yxatda topilib, muvaffaqiyatli biriktirilgan bo'lsa
     */
    public synchronized boolean assignFaculty(Long chatId, int facultyId) {
        GroupInfo info = groups.get(chatId);
        if (info == null) {
            return false;
        }
        info.setFacultyId(facultyId);
        saveToFile();
        log.info(">>> Guruh \"{}\" (ID: {}) \"{}\" fakultetiga biriktirildi.",
                info.getTitle(), chatId, Faculty.byId(facultyId).map(Faculty::getDisplayName).orElse("?"));
        printAllGroups();
        return true;
    }

    /**
     * Shu chatId ro'yxatdan ma'lumotini qaytaradi (topilmasa {@code null}).
     */
    public GroupInfo getGroup(Long chatId) {
        return groups.get(chatId);
    }

    /**
     * Ko'rsatilgan fakultetga biriktirilgan barcha guruhlarning chat ID larini qaytaradi.
     */
    public List<Long> getGroupIdsByFaculty(int facultyId) {
        return groups.values().stream()
                .filter(g -> g.getFacultyId() != null && g.getFacultyId() == facultyId)
                .map(GroupInfo::getChatId)
                .collect(Collectors.toList());
    }

    /**
     * Ro'yxatdagi barcha guruhlarning chat ID larini qaytaradi (masalan,
     * "barchasi" hashtagi ishlatilganda barcha guruhlarga jo'natish uchun).
     */
    public List<Long> getAllGroupIds() {
        return new ArrayList<>(groups.keySet());
    }

    /**
     * Ro'yxatdagi barcha guruhlar haqidagi to'liq ma'lumotni qaytaradi (faqat o'qish uchun).
     */
    public Collection<GroupInfo> getAllGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    /**
     * Ro'yxatdagi barcha guruhlarni (va ularning fakultetini) konsolga chiqaradi.
     */
    public void printAllGroups() {
        log.info("========== Ro'yxatdagi barcha guruhlar ({} ta) ==========", groups.size());
        if (groups.isEmpty()) {
            log.info("Hozircha hech qanday guruh ro'yxatga olinmagan.");
        } else {
            groups.values().forEach(g -> {
                String facultyName = g.getFacultyId() == null
                        ? "BIRIKTIRILMAGAN"
                        : Faculty.byId(g.getFacultyId()).map(Faculty::getDisplayName).orElse("noma'lum");
                log.info("  -> ID: {} | turi: {} | nomi: \"{}\" | username: {} | fakulteti: {}",
                        g.getChatId(), g.getChatType(), g.getTitle(), g.getUsername(), facultyName);
            });
        }
        log.info("===========================================================");
    }

    private void loadFromFile() {
        if (!storageFile.exists()) {
            log.info("Guruhlar fayli topilmadi ({}), bo'sh ro'yxat bilan boshlanmoqda.", storageFile.getAbsolutePath());
            return;
        }
        try {
            List<GroupInfo> list = objectMapper.readValue(storageFile, new TypeReference<List<GroupInfo>>() {
            });
            for (GroupInfo info : list) {
                groups.put(info.getChatId(), info);
            }
            log.info("{} ta guruh '{}' faylidan muvaffaqiyatli yuklandi.", groups.size(), storageFile.getAbsolutePath());
            printAllGroups();
        } catch (IOException e) {
            log.error("Guruhlar faylini o'qishda xatolik yuz berdi: {}", e.getMessage(), e);
        }
    }

    private void saveToFile() {
        try {
            objectMapper.writeValue(storageFile, new ArrayList<>(groups.values()));
        } catch (IOException e) {
            log.error("Guruhlar faylini saqlashda xatolik yuz berdi: {}", e.getMessage(), e);
        }
    }
}
