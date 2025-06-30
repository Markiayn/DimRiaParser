package org.example.scheduler;

import org.example.config.AppConfig;
import org.example.service.PostingService;
import org.example.service.RiaParserService;
import org.example.utils.FileUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoPostingScheduler {
    private final ScheduledExecutorService scheduler;
    private final RiaParserService parserService;
    private final PostingService postingService;
    private final boolean verbose;
    
    public AutoPostingScheduler() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.parserService = new RiaParserService();
        this.postingService = new PostingService();
        this.verbose = AppConfig.isVerbose();
    }
    
    /**
     * Запускає автоматичний постинг з розкладом:
     * - 8:00 - парсинг нових оголошень
     * - 9:00 - постинг 2 найновіших оголошень (різні в різні канали)
     * - 10:00-22:00 - щогодинний постинг (нові з останньої години або ранкові)
     */
    public void startScheduledPosting() {
        System.out.println("🚀 Запуск автоматичного постингу...");
        
        // Перевіряємо підключення до Telegram
        if (!postingService.testTelegramConnection()) {
            System.err.println("❌ Помилка підключення до Telegram. Перевірте налаштування.");
            return;
        }
        
        // Розраховуємо затримки до наступних подій
        long delayTo8AM = calculateDelayToTime(8, 0);
        long delayTo9AM = calculateDelayToTime(9, 0);
        long delayTo10AM = calculateDelayToTime(10, 0);
        
        if (verbose) {
            System.out.println("⏰ Затримка до парсингу (8:00): " + formatDelay(delayTo8AM));
            System.out.println("⏰ Затримка до ранкового постингу (9:00): " + formatDelay(delayTo9AM));
            System.out.println("⏰ Затримка до щогодинного постингу (10:00): " + formatDelay(delayTo10AM));
        }
        
        // Парсинг о 8:00
        scheduler.scheduleAtFixedRate(
            this::runMorningParsing,
            delayTo8AM,
            TimeUnit.DAYS.toSeconds(1), // Кожен день
            TimeUnit.SECONDS
        );
        
        // Ранковий постинг о 9:00 (2 найновіші оголошення)
        scheduler.scheduleAtFixedRate(
            this::runMorningPosting,
            delayTo9AM,
            TimeUnit.DAYS.toSeconds(1), // Кожен день
            TimeUnit.SECONDS
        );
        
        // Щогодинний постинг з 10:00 до 22:00
        scheduler.scheduleAtFixedRate(
            this::runHourlyPosting,
            delayTo10AM,
            TimeUnit.HOURS.toSeconds(1), // Кожну годину
            TimeUnit.SECONDS
        );
        
        System.out.println("✅ Автоматичний постинг запущено!");
        System.out.println("📅 Розклад:");
        System.out.println("   🕐 8:00 - Парсинг нових оголошень");
        System.out.println("   🕐 9:00 - Постинг 2 найновіших оголошень (різні канали)");
        System.out.println("   🕐 10:00-22:00 - Щогодинний постинг (нові або ранкові)");
    }
    
    /**
     * Ранковий парсинг о 8:00
     */
    private void runMorningParsing() {
        try {
            System.out.println("\n🌅 Починаємо ранковий парсинг (8:00)...");
            parserService.parseApartments();
            System.out.println("✅ Ранковий парсинг завершено!");
        } catch (Exception e) {
            System.err.println("❌ Помилка ранкового парсингу: " + e.getMessage());
        }
    }
    
    /**
     * Ранковий постинг о 9:00 (2 найновіші оголошення)
     */
    private void runMorningPosting() {
        try {
            System.out.println("\n🌅 Починаємо ранковий постинг (9:00)...");
            boolean success = postingService.postMorningApartments();
            
            if (success) {
                System.out.println("✅ Ранковий постинг завершено!");
            } else {
                System.out.println("⚠️ Ранковий постинг не вдався - немає оголошень");
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка ранкового постингу: " + e.getMessage());
        }
    }
    
    /**
     * Щогодинний постинг з 10:00 до 22:00
     */
    private void runHourlyPosting() {
        LocalTime currentTime = LocalTime.now();
        
        // Перевіряємо чи поточний час в межах 10:00-22:00
        if (currentTime.isBefore(LocalTime.of(10, 0)) || currentTime.isAfter(LocalTime.of(22, 0))) {
            if (verbose) {
                System.out.println("⏰ Щогодинний постинг пропущено (поза робочими годинами 10:00-22:00)");
            }
            return;
        }
        
        try {
            System.out.println("\n⏰ Починаємо щогодинний постинг (" + currentTime.getHour() + ":00)...");
            boolean success = postingService.postHourlyApartments();
            
            if (success) {
                System.out.println("✅ Щогодинний постинг завершено!");
            } else {
                System.out.println("⚠️ Щогодинний постинг не вдався - немає оголошень");
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка щогодинного постингу: " + e.getMessage());
        }
    }
    
    /**
     * Розраховує затримку до вказаного часу
     */
    private long calculateDelayToTime(int hour, int minute) {
        LocalTime targetTime = LocalTime.of(hour, minute);
        LocalTime now = LocalTime.now();
        
        long delaySeconds = 0;
        
        if (now.isBefore(targetTime)) {
            // Сьогодні
            delaySeconds = java.time.Duration.between(now, targetTime).getSeconds();
        } else {
            // Завтра
            delaySeconds = java.time.Duration.between(now, LocalTime.MAX).getSeconds() + 1 +
                         java.time.Duration.between(LocalTime.MIN, targetTime).getSeconds();
        }
        
        return delaySeconds;
    }
    
    /**
     * Форматує затримку для виведення
     */
    private String formatDelay(long delaySeconds) {
        long hours = delaySeconds / 3600;
        long minutes = (delaySeconds % 3600) / 60;
        long seconds = delaySeconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Зупиняє планувальник
     */
    public void stop() {
        System.out.println("🛑 Зупинка автоматичного постингу...");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("✅ Автоматичний постинг зупинено!");
    }
    
    /**
     * Запускає тестовий постинг
     */
    public void runTestPosting() {
        System.out.println("🧪 Запуск тестового постингу...");
        
        if (!postingService.testTelegramConnection()) {
            System.err.println("❌ Помилка підключення до Telegram");
            return;
        }
        
        if (postingService.sendTestMessage()) {
            System.out.println("✅ Тестовий постинг успішний!");
        } else {
            System.err.println("❌ Тестовий постинг не вдався");
        }
    }
    
    /**
     * Запускає повний тестовий режим (парсинг + постинг)
     */
    public void runFullTestMode() {
        System.out.println("🧪 Запуск повного тестового режиму (парсинг + постинг)...");
        
        if (!postingService.testTelegramConnection()) {
            System.err.println("❌ Помилка підключення до Telegram");
            return;
        }
        
        if (postingService.runTestMode()) {
            System.out.println("✅ Повний тестовий режим завершено успішно!");
        } else {
            System.err.println("❌ Повний тестовий режим не вдався");
        }
    }
} 