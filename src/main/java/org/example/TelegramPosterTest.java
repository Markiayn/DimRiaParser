package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

public class TelegramPosterTest extends TelegramLongPollingBot {

    private final String botToken;
    private final String botUsername;

    public TelegramPosterTest(String botToken, String botUsername) {
        this.botToken = botToken;
        this.botUsername = botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(org.telegram.telegrambots.meta.api.objects.Update update) {
        // ми нічого не приймаємо в цьому боті
    }

    public void sendText(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
            System.out.println("✅ Повідомлення надіслано: " + text);
        } catch (TelegramApiException e) {
            System.err.println("❌ Помилка надсилання повідомлення: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        // 🔐 ТУТ ВСТАВ СВІЙ ТОКЕН І ЮЗЕРНЕЙМ БОТА
        String token = "7731493593:AAGK9ckp-CeIpbSRzxIphKF59jhL7n1UnP8";
        String username = "DimRiaPasrer_bot";
        String chatId = "@DimRiaParser_Lviv_2"; // або -1001234567890 для приватного

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        TelegramPosterTest bot = new TelegramPosterTest(token, username);

        botsApi.registerBot(bot);

        // Тестове повідомлення
        bot.sendText(chatId, "🔔 Я люблю марічку");
    }
}
