package org.example.scheduler;

import org.example.config.AppConfig;
import org.example.service.PostingService;
import org.example.service.RiaParserService;

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
    
    public void startScheduledPosting() {
        System.out.println("Запуск автоматичного постингу...");
        
        if (!postingService.testTelegramConnection()) {
            System.err.println("Помилка підключення до Telegram. Перевірте налаштування.");
            return;
        }
        
        long delayTo8AM = calculateDelayToTime(8, 0);
        long delayTo10AM = calculateDelayToTime(10, 0);
        if (verbose) {
            System.out.println("Затримка до парсингу (8:00): " + formatDelay(delayTo8AM));
            System.out.println("Затримка до щогодинного постингу (10:00): " + formatDelay(delayTo10AM));
        }
        // Ранковий парсинг о 8:00
        scheduler.scheduleAtFixedRate(
            this::runMorningParsing,
            delayTo8AM,
            TimeUnit.DAYS.toSeconds(1),
            TimeUnit.SECONDS
        );
        // Щогодинний постинг з 10:00
        scheduler.scheduleAtFixedRate(
            this::runHourlyPosting,
            delayTo10AM,
            TimeUnit.HOURS.toSeconds(1),
            TimeUnit.SECONDS
        );
        System.out.println("Автоматичний постинг запущено!");
        System.out.println("Розклад: 8:00 - Парсинг нових оголошень; 10:00-22:00 - Щогодинний постинг (нові за останню годину)");
    }
    
    public void startScheduledPostingFromNow() {
        System.out.println("Запуск автоматичного постингу з поточного моменту...");
        
        if (!postingService.testTelegramConnection()) {
            System.err.println("Помилка підключення до Telegram. Перевірте налаштування.");
            return;
        }
        
        java.time.LocalTime now = java.time.LocalTime.now();
        System.out.println("Поточний час: " + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        
        long delayToNextHour = calculateDelayToNextHour();
        if (verbose) {
            System.out.println("Затримка до першого постингу: " + formatDelay(delayToNextHour));
        }
        
        scheduler.scheduleAtFixedRate(
            this::runHourlyPosting,
            delayToNextHour,
            TimeUnit.HOURS.toSeconds(1),
            TimeUnit.SECONDS
        );
        
        java.time.LocalTime nextHour = java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1);
        System.out.println("Автоматичний постинг з поточного моменту запущено!");
        System.out.println("Розклад: " + nextHour.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " - Перший постинг, далі щогодинно до 22:00");
    }
    
    private void runHourlyPosting() {
        java.time.LocalTime currentTime = java.time.LocalTime.now();
        if (currentTime.getHour() < 10 || currentTime.getHour() > 22) {
            if (verbose) {
                System.out.println("Щогодинний постинг пропущено (поза робочими часами 10:00-22:00)");
            }
            return;
        }
        try {
            // Спочатку постимо
            System.out.println("Починаємо щогодинний постинг (" + currentTime.getHour() + ":00) для всіх міст...");
            postingService.publishPostsForAllCitiesWithSmartLogic(2); // 2 пости на місто за останню годину
            System.out.println("Щогодинний постинг завершено!");
            // Після постингу парсимо нові оголошення
            System.out.println("Парсинг нових оголошень після постингу (" + currentTime.getHour() + ":00) для всіх міст...");
            parserService.parseApartmentsForAllCities();
            System.out.println("Парсинг завершено!");
        } catch (Exception e) {
            System.err.println("Помилка щогодинного постингу: " + e.getMessage());
        }
    }
    
    private void runMorningParsing() {
        try {
            System.out.println("\nПочинаємо ранковий парсинг (8:00) для всіх міст...");
            parserService.parseApartmentsForAllCities();
            System.out.println("Ранковий парсинг завершено!");
        } catch (Exception e) {
            System.err.println("Помилка ранкового парсингу: " + e.getMessage());
        }
    }
    
    private long calculateDelayToTime(int hour, int minute) {
        LocalTime targetTime = LocalTime.of(hour, minute);
        LocalTime now = LocalTime.now();
        
        long delaySeconds = 0;
        
        if (now.isBefore(targetTime)) {
            delaySeconds = java.time.Duration.between(now, targetTime).getSeconds();
        } else {
            delaySeconds = java.time.Duration.between(now, LocalTime.MAX).getSeconds() + 1 +
                         java.time.Duration.between(LocalTime.MIN, targetTime).getSeconds();
        }
        
        return delaySeconds;
    }
    
    private long calculateDelayToNextHour() {
        java.time.LocalTime now = java.time.LocalTime.now();
        java.time.LocalTime nextHour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1);
        return java.time.Duration.between(now, nextHour).getSeconds();
    }
    
    private String formatDelay(long delaySeconds) {
        long hours = delaySeconds / 3600;
        long minutes = (delaySeconds % 3600) / 60;
        long seconds = delaySeconds % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    public void stop() {
        System.out.println("Зупинка автоматичного постингу...");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Автоматичний постинг зупинено!");
    }
} 