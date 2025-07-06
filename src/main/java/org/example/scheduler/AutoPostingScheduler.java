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
        System.out.println("=== ЗАПУСК АВТОМАТИЧНОГО РЕЖИМУ ===");
        System.out.println("Поточний час: " + LocalTime.now());
        System.out.println("Дата: " + java.time.LocalDate.now());
        
        if (!postingService.testTelegramConnection()) {
            System.err.println("Помилка підключення до Telegram. Перевірте налаштування.");
            return;
        }
        
        long delayTo8AM = calculateDelayToTime(8, 0);
        long delayTo10AM = calculateDelayToTime(10, 0);
        
        System.out.println("=== РОЗРАХУНОК ЗАТРИМОК ===");
        System.out.println("Поточний час: " + LocalTime.now());
        System.out.println("Затримка до парсингу (8:00): " + formatDelay(delayTo8AM));
        System.out.println("Затримка до щогодинного постингу (10:00): " + formatDelay(delayTo10AM));
        System.out.println("Наступний ранковий парсинг о 8:00 через: " + formatDelay(delayTo8AM));
        System.out.println("Наступний постинг о 10:00 через: " + formatDelay(delayTo10AM));
        
        // Розраховуємо точну дату наступного запуску
        java.time.LocalDateTime nextParsingTime = java.time.LocalDateTime.now().plusSeconds(delayTo8AM);
        java.time.LocalDateTime nextPostingTime = java.time.LocalDateTime.now().plusSeconds(delayTo10AM);
        System.out.println("Наступний парсинг заплановано на: " + nextParsingTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        System.out.println("Наступний постинг заплановано на: " + nextPostingTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        
        // Ранковий парсинг о 8:00
        System.out.println("=== ПЛАНУВАННЯ РАНКОВОГО ПАРСИНГУ ===");
        scheduleNextMorningParsing(delayTo8AM);
        System.out.println("Ранковий парсинг заплановано на 8:00 щодня");
        
        // Щогодинний постинг з 10:00
        System.out.println("=== ПЛАНУВАННЯ ЩОГОДИННОГО ПОСТИНГУ ===");
        scheduleNextHourlyPosting(delayTo10AM);
        System.out.println("Щогодинний постинг заплановано з 10:00 щогодини");
        
        System.out.println("=== АВТОМАТИЧНИЙ РЕЖИМ ЗАПУЩЕНО ===");
        System.out.println("Розклад: 8:00 - Парсинг нових оголошень; 10:00-22:00 - Щогодинний постинг");
        System.out.println("Scheduler статус: " + (scheduler.isShutdown() ? "ЗУПИНЕНО" : "ПРАЦЮЄ"));
    }
    
    public void startScheduledPostingFromNow() {
        System.out.println("Запуск автоматичного постингу з поточного моменту...");

        if (!postingService.testTelegramConnection()) {
            System.err.println("Помилка підключення до Telegram. Перевірте налаштування.");
            return;
        }

        Thread autonowThread = new Thread(() -> {
            try {
                // 1. Одразу парсимо
                System.out.println("Одразу парсимо оголошення...");
                parserService.parseApartmentsForAllCities();

                while (true) {
                    java.time.LocalTime now = java.time.LocalTime.now();
                    int currentHour = now.getHour();
                    if (currentHour >= 22) {
                        System.out.println("Робочий день завершено (після 22:00). Автоматичний режим зупиняється.");
                        break;
                    }
                    // 2. Чекаємо до найближчої повної години
                    java.time.LocalTime nextHour = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1);
                    long secondsToNextHour = java.time.Duration.between(now, nextHour).getSeconds();
                    if (verbose) {
                        System.out.println("Чекаємо до наступної години: " + nextHour + " (" + formatDelay(secondsToNextHour) + ")");
                    }
                    Thread.sleep(secondsToNextHour * 1000);

                    // 3. Постимо
                    java.time.LocalTime postTime = java.time.LocalTime.now();
                    if (postTime.getHour() < 10 || postTime.getHour() > 22) {
                        if (verbose) {
                            System.out.println("Постинг пропущено (поза робочими годинами 10:00-22:00)");
                        }
                        continue;
                    }
                    System.out.println("Починаємо постинг (" + postTime.getHour() + ":00) для всіх міст...");
                    postingService.publishPostsForAllCitiesWithSmartLogic(2);
                    System.out.println("Постинг завершено!");

                    // 4. Одразу парсимо для наступної години
                    System.out.println("Парсинг нових оголошень після постингу (" + postTime.getHour() + ":00) для всіх міст...");
                    parserService.parseApartmentsForAllCities();
                    System.out.println("Парсинг завершено!");
                    // 5. Чекаємо 1 годину (до наступної повної години)
                    // (наступна ітерація циклу)
                }
            } catch (InterruptedException e) {
                System.out.println("Автоматичний режим з поточного моменту зупинено!");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Помилка в автоматичному режимі: " + e.getMessage());
            }
        });
        autonowThread.setDaemon(true);
        autonowThread.start();
        System.out.println("Автоматичний постинг з поточного моменту запущено!");
        System.out.println("Розклад: постинг щогодини з 10:00 до 22:00. Перший постинг буде о найближчій повній годині.");
    }
    
    private void runHourlyPosting() {
        java.time.LocalTime currentTime = java.time.LocalTime.now();
        System.out.println("\n=== ЩОГОДИННИЙ ПОСТИНГ ===");
        System.out.println("Поточний час: " + currentTime);
        System.out.println("Дата: " + java.time.LocalDate.now());
        
        if (currentTime.getHour() < 10 || currentTime.getHour() > 22) {
            System.out.println("Щогодинний постинг пропущено (поза робочими часами 10:00-22:00)");
            // Плануємо наступний запуск на завтра о 10:00
            long nextDelay = calculateDelayToTime(10, 0);
            scheduleNextHourlyPosting(nextDelay);
            return;
        }
        
        try {
            System.out.println("Починаємо щогодинний постинг (" + currentTime.getHour() + ":00) для всіх міст...");
            postingService.publishPostsForAllCitiesWithSmartLogic(2);
            System.out.println("Щогодинний постинг завершено!");
            
            System.out.println("Парсинг нових оголошень після постингу (" + currentTime.getHour() + ":00) для всіх міст...");
            parserService.parseApartmentsForAllCities();
            System.out.println("Парсинг завершено!");
            
            System.out.println("=== ЩОГОДИННИЙ ЦИКЛ ЗАВЕРШЕНО ===");
            
            // Плануємо наступний запуск через годину
            long nextDelay = calculateDelayToNextHour();
            scheduleNextHourlyPosting(nextDelay);
        } catch (Exception e) {
            System.err.println("ПОМИЛКА щогодинного постингу: " + e.getMessage());
            e.printStackTrace();
            // Навіть при помилці плануємо наступний запуск
            long nextDelay = calculateDelayToNextHour();
            scheduleNextHourlyPosting(nextDelay);
        }
    }
    
    private void runMorningParsing() {
        System.out.println("\n=== РАНКОВИЙ ПАРСИНГ ===");
        System.out.println("Поточний час: " + LocalTime.now());
        System.out.println("Дата: " + java.time.LocalDate.now());
        
        try {
            System.out.println("Починаємо ранковий парсинг (8:00) для всіх міст...");
            System.out.println("Очищення таблиць і фото...");
            parserService.parseApartmentsForAllCitiesMorning();
            System.out.println("Ранковий парсинг завершено!");
            System.out.println("=== РАНКОВИЙ ПАРСИНГ ЗАВЕРШЕНО ===");
            
            // Показуємо наступний запланований запуск
            long nextDelay = calculateDelayToTime(8, 0);
            java.time.LocalDateTime nextRun = java.time.LocalDateTime.now().plusSeconds(nextDelay);
            System.out.println("Наступний ранковий парсинг заплановано на: " + nextRun.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
            
            // Плануємо наступний запуск на завтра о 8:00
            scheduleNextMorningParsing(nextDelay);
        } catch (Exception e) {
            System.err.println("ПОМИЛКА ранкового парсингу: " + e.getMessage());
            e.printStackTrace();
            // Навіть при помилці плануємо наступний запуск на завтра о 8:00
            long nextDelay = calculateDelayToTime(8, 0);
            scheduleNextMorningParsing(nextDelay);
        }
    }
    
    private long calculateDelayToTime(int hour, int minute) {
        LocalTime targetTime = LocalTime.of(hour, minute);
        LocalTime now = LocalTime.now();
        
        long delaySeconds = 0;
        
        if (now.isBefore(targetTime)) {
            // Якщо поточний час раніше цільового - чекаємо до цього ж дня
            delaySeconds = java.time.Duration.between(now, targetTime).getSeconds();
        } else {
            // Якщо поточний час після цільового - чекаємо до наступного дня
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
    
    private void scheduleNextMorningParsing(long delaySeconds) {
        scheduler.schedule(() -> {
            runMorningParsing();
            // Наступний запуск буде запланований всередині runMorningParsing
            // на завтра о 8:00
        }, delaySeconds, TimeUnit.SECONDS);
    }
    
    private void scheduleNextHourlyPosting(long delaySeconds) {
        scheduler.schedule(() -> {
            runHourlyPosting();
            // Наступний запуск буде запланований всередині runHourlyPosting
            // в залежності від поточного часу
        }, delaySeconds, TimeUnit.SECONDS);
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

    // Тестовий режим для швидкої перевірки логіки автоматичного постингу
    public void startTestScheduledPosting() {
        System.out.println("Тестовий запуск автоматичного постингу (швидкий цикл)...");
        // 1. Очищення і парсинг як о 8:00
        parserService.parseApartmentsForAllCitiesMorning();
        // 2. Далі цикл: постинг -> парсинг -> чекати 10 секунд
        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) { // 3 ітерації для тесту
                    System.out.println("Тестовий постинг (імітація 10:00+)");
                    postingService.publishPostsForAllCitiesWithSmartLogic(2);
                    System.out.println("Тестовий парсинг після постингу");
                    parserService.parseApartmentsForAllCities();
                    Thread.sleep(10000); // 10 секунд між ітераціями
                }
                System.out.println("Тест завершено!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
} 