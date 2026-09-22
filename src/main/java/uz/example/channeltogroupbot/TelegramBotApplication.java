package uz.example.channeltogroupbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ilovaning kirish nuqtasi (entry point).
 * Spring Boot konteksti shu yerdan ishga tushadi va u BotInitializer orqali
 * Telegram botini avtomatik ravishda ro'yxatdan o'tkazadi (@PostConstruct).
 */
@SpringBootApplication
public class TelegramBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramBotApplication.class, args);
    }
}
