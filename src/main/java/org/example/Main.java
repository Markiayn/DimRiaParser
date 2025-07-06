package org.example;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.scheduler.AutoPostingScheduler;
import org.example.service.PostingService;
import org.example.service.RiaParserService;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.println("DimRiaParser - Парсер оголошень з dom.ria.com");
        System.out.println("================================================");
        
        if (!validateConfiguration()) {
            System.exit(1);
        }
        
        initializeDatabase();
        
        if (args.length > 0) {
            handleCommandLineArgs(args);
            return;
        }
        
        runInteractiveMode();
    }
    
    private static boolean validateConfiguration() {
        System.out.println("Перевірка конфігурації...");
        
        String chromeDriverPath = AppConfig.getChromeDriverPath();
        if (!new java.io.File(chromeDriverPath).exists()) {
            System.err.println("ChromeDriver не знайдено: " + chromeDriverPath);
            return false;
        }
        
        String botToken = AppConfig.getTelegramBotToken();
        String chatId1 = AppConfig.getTelegramChatId1();
        String chatId2 = AppConfig.getTelegramChatId2();
        
        if ("your_bot_token_here".equals(botToken) || 
            "your_chat_id1_here".equals(chatId1) || 
            "your_chat_id2_here".equals(chatId2)) {
            System.err.println("Налаштування Telegram не завершено. Перевірте config.properties");
            return false;
        }
        
        System.out.println("Конфігурація в порядку");
        return true;
    }
    
    private static void initializeDatabase() {
        System.out.println("Ініціалізація бази даних...");
        
        DatabaseManager dbManager = DatabaseManager.getInstance();
        
        for (org.example.config.CityConfig.City city : org.example.config.CityConfig.getCities()) {
            dbManager.createTable(city.dbTable);
        }
        
        System.out.println("База даних ініціалізована");
    }
    
    private static void handleCommandLineArgs(String[] args) {
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "parse":
                System.out.println("Запуск парсингу...");
                RiaParserService parser = new RiaParserService();
                parser.parseApartmentsForAllCities();
                break;
                
            case "post":
                System.out.println("Запуск постингу...");
                PostingService postingService = new PostingService();
                postingService.postMorningApartmentsForAllCities(org.example.config.CityConfig.getCities());
                break;
                
            case "auto":
                System.out.println("Запуск автоматичного режиму...");
                AutoPostingScheduler scheduler = new AutoPostingScheduler();
                scheduler.startScheduledPosting();
                
                Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
                
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    scheduler.stop();
                }
                break;
                
            case "autonow":
                System.out.println("Запуск автоматичного режиму з поточного моменту...");
                AutoPostingScheduler schedulerNow = new AutoPostingScheduler();
                schedulerNow.startScheduledPostingFromNow();
                
                Runtime.getRuntime().addShutdownHook(new Thread(schedulerNow::stop));
                
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    schedulerNow.stop();
                }
                break;
                
            case "test":
                System.out.println("Запуск тестового автоматичного режиму (швидкий цикл)...");
                AutoPostingScheduler testScheduler = new AutoPostingScheduler();
                testScheduler.startTestScheduledPosting();
                break;
                
            default:
                System.err.println("Невідома команда: " + command);
                printUsage();
                break;
        }
    }
    
    private static void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println("\nВиберіть опцію:");
            System.out.println("1. Парсинг оголошень");
            System.out.println("2. Постинг оголошень");
            System.out.println("3. Автоматичний режим (з 8:00)");
            System.out.println("4. Автоматичний режим (з поточного моменту)");
            System.out.println("5. Вихід");
            System.out.println("6. Тестовий автоматичний режим (швидкий цикл)");
            System.out.print("Ваш вибір: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    runParsing();
                    break;
                    
                case "2":
                    runPosting();
                    break;
                    
                case "3":
                    runAutoMode();
                    break;
                    
                case "4":
                    runAutoModeFromNow();
                    break;
                    
                case "5":
                    System.out.println("До побачення!");
                    return;
                    
                case "6":
                    runTestAutoMode();
                    break;
                    
                default:
                    System.out.println("Невірний вибір. Спробуйте ще раз.");
                    break;
            }
        }
    }
    
    private static void runParsing() {
        System.out.println("\nПочинаємо парсинг...");
        try {
            RiaParserService parser = new RiaParserService();
            parser.parseApartmentsForAllCities();
            System.out.println("Парсинг завершено!");
        } catch (Exception e) {
            System.err.println("Помилка парсингу: " + e.getMessage());
        }
    }
    
    private static void runPosting() {
        System.out.println("\nПочинаємо постинг...");
        try {
            PostingService postingService = new PostingService();
            postingService.postMorningApartmentsForAllCities(org.example.config.CityConfig.getCities());
            System.out.println("Постинг завершено");
        } catch (Exception e) {
            System.err.println("Помилка постингу: " + e.getMessage());
        }
    }
    
    private static void runAutoMode() {
        System.out.println("\nЗапуск автоматичного режиму...");
        System.out.println("Для зупинки натисніть Ctrl+C");
        
        AutoPostingScheduler scheduler = new AutoPostingScheduler();
        scheduler.startScheduledPosting();
        
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            scheduler.stop();
        }
    }
    
    private static void runAutoModeFromNow() {
        System.out.println("\nЗапуск автоматичного режиму з поточного моменту...");
        System.out.println("Для зупинки натисніть Ctrl+C");
        
        AutoPostingScheduler scheduler = new AutoPostingScheduler();
        scheduler.startScheduledPostingFromNow();
        
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            scheduler.stop();
        }
    }
    
    private static void runTestAutoMode() {
        System.out.println("\nЗапуск тестового автоматичного режиму (швидкий цикл)...");
        AutoPostingScheduler scheduler = new AutoPostingScheduler();
        scheduler.startTestScheduledPosting();
    }
    
    private static void printUsage() {
        System.out.println("\nВикористання:");
        System.out.println("  java -jar DimRiaParser.jar [команда]");
        System.out.println("\nДоступні команди:");
        System.out.println("  parse   - Парсинг оголошень");
        System.out.println("  post    - Постинг оголошень");
        System.out.println("  auto    - Автоматичний режим (з 8:00)");
        System.out.println("  autonow - Автоматичний режим (з поточного моменту)");
        System.out.println("  test    - Тестовий автоматичний режим (швидкий цикл)");
        System.out.println("\nБез аргументів запускається інтерактивний режим");
    }
}