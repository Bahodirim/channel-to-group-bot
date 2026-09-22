package uz.example.channeltogroupbot.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.example.channeltogroupbot.bot.ChannelToGroupBot;

/**
 * Spring konteksti ishga tushishi bilan Telegram botini Telegram Bot API'ga
 * (long-polling usulida) ro'yxatdan o'tkazadi.
 */
@Component
public class BotInitializer {

    private static final Logger log = LoggerFactory.getLogger(BotInitializer.class);

    private final ChannelToGroupBot bot;

    public BotInitializer(ChannelToGroupBot bot) {
        this.bot = bot;
    }

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(bot);
            log.info("Bot muvaffaqiyatli ishga tushdi va Telegram serverlariga ulandi: @{}", bot.getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Botni ishga tushirishda xatolik yuz berdi: {}", e.getMessage(), e);
        }
    }
}
