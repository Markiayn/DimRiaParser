package org.example;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.scheduler.AutoPostingScheduler;
import org.example.service.PostingService;
import org.example.service.RiaParserService;
import org.example.utils.FileUtils;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.println("🏠 DimRiaParser - Парсер оголошень з dom.ria.com");
        System.out.println("================================================");
        
        // Перевіряємо конфігурацію
        if (!validateConfiguration()) {
            System.exit(1);
        }
        
        // Ініціалізуємо базу даних
        initializeDatabase();
        
        // Обробляємо аргументи командного рядка
        if (args.length > 0) {
            handleCommandLineArgs(args);
            return;
        }
        
        // Інтерактивний режим
        runInteractiveMode();
    }
    
    private static boolean validateConfiguration() {
        System.out.println("🔧 Перевірка конфігурації...");
        
        // Перевіряємо наявність ChromeDriver
        String chromeDriverPath = AppConfig.getChromeDriverPath();
        if (!new java.io.File(chromeDriverPath).exists()) {
            System.err.println("❌ ChromeDriver не знайдено: " + chromeDriverPath);
            return false;
        }
        
        // Перевіряємо налаштування Telegram
        String botToken = AppConfig.getTelegramBotToken();
        String chatId1 = AppConfig.getTelegramChatId1();
        String chatId2 = AppConfig.getTelegramChatId2();
        
        if ("your_bot_token_here".equals(botToken) || 
            "your_chat_id1_here".equals(chatId1) || 
            "your_chat_id2_here".equals(chatId2)) {
            System.err.println("❌ Налаштування Telegram не завершено. Перевірте config.properties");
            return false;
        }
        
        System.out.println("✅ Конфігурація в порядку");
        return true;
    }
    
    private static void initializeDatabase() {
        System.out.println("🗄 Ініціалізація бази даних...");
        
        DatabaseManager dbManager = DatabaseManager.getInstance();
        
        // Створюємо таблиці для обох областей
        dbManager.createTable("Apartments_Lviv");
        dbManager.createTable("Apartments_IvanoFrankivsk");
        
        System.out.println("✅ База даних ініціалізована");
    }
    
    private static void handleCommandLineArgs(String[] args) {
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "parse":
                System.out.println("🔄 Запуск парсингу...");
                RiaParserService parser = new RiaParserService();
                parser.parseApartments();
                break;
                
            case "post":
                System.out.println("📤 Запуск постингу...");
                PostingService postingService = new PostingService();
                if (postingService.postMorningApartments()) {
                    System.out.println("✅ Постинг завершено");
                } else {
                    System.out.println("⚠️ Постинг не вдався - немає оголошень");
                }
                break;
                
            case "auto":
                System.out.println("🤖 Запуск автоматичного режиму...");
                AutoPostingScheduler scheduler = new AutoPostingScheduler();
                scheduler.startScheduledPosting();
                
                // Чекаємо сигнал для зупинки
                Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
                
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    scheduler.stop();
                }
                break;
                
            case "test":
                System.out.println("🧪 Запуск тестового постингу...");
                AutoPostingScheduler testScheduler = new AutoPostingScheduler();
                testScheduler.runTestPosting();
                break;
                
            case "testfull":
                System.out.println("🧪 Запуск повного тестового режиму...");
                AutoPostingScheduler fullTestScheduler = new AutoPostingScheduler();
                fullTestScheduler.runFullTestMode();
                break;
                
            case "testcycle":
                System.out.println("🧪 Запуск тестового циклу з кастомним таймінгом...");
                runTestCycle();
                break;
                
            default:
                System.err.println("❌ Невідома команда: " + command);
                printUsage();
                break;
        }
    }
    
    private static void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println("\n📋 Виберіть опцію:");
            System.out.println("1. 🔄 Парсинг оголошень");
            System.out.println("2. 📤 Постинг оголошень");
            System.out.println("3. 🤖 Автоматичний режим");
            System.out.println("4. 🧪 Тестовий постинг");
            System.out.println("5. 🧪 Повний тестовий режим");
            System.out.println("6. 🧪 Тестовий цикл з кастомним таймінгом");
            System.out.println("7. ❌ Вихід");
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
                    runTestPosting();
                    break;
                    
                case "5":
                    runFullTestMode();
                    break;
                    
                case "6":
                    runTestCycle();
                    break;
                    
                case "7":
                    System.out.println("👋 До побачення!");
                    return;
                    
                default:
                    System.out.println("❌ Невірний вибір. Спробуйте ще раз.");
                    break;
            }
        }
    }
    
    private static void runParsing() {
        System.out.println("\n🔄 Починаємо парсинг...");
        try {
            RiaParserService parser = new RiaParserService();
            parser.parseApartments();
            System.out.println("✅ Парсинг завершено!");
        } catch (Exception e) {
            System.err.println("❌ Помилка парсингу: " + e.getMessage());
        }
    }
    
    private static void runPosting() {
        System.out.println("\n📤 Починаємо постинг...");
        try {
            PostingService postingService = new PostingService();
            if (postingService.postMorningApartments()) {
                System.out.println("✅ Постинг завершено!");
            } else {
                System.out.println("⚠️ Постинг не вдався - немає оголошень");
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка постингу: " + e.getMessage());
        }
    }
    
    private static void runAutoMode() {
        System.out.println("\n🤖 Запуск автоматичного режиму...");
        System.out.println("💡 Для зупинки натисніть Ctrl+C");
        
        AutoPostingScheduler scheduler = new AutoPostingScheduler();
        scheduler.startScheduledPosting();
        
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            scheduler.stop();
        }
    }
    
    private static void runTestPosting() {
        System.out.println("\n🧪 Запуск тестового постингу...");
        try {
            AutoPostingScheduler scheduler = new AutoPostingScheduler();
            scheduler.runTestPosting();
        } catch (Exception e) {
            System.err.println("❌ Помилка тестового постингу: " + e.getMessage());
        }
    }
    
    private static void runFullTestMode() {
        System.out.println("\n🧪 Запуск повного тестового режиму...");
        try {
            AutoPostingScheduler fullTestScheduler = new AutoPostingScheduler();
            fullTestScheduler.runFullTestMode();
        } catch (Exception e) {
            System.err.println("❌ Помилка повного тестового режиму: " + e.getMessage());
        }
    }
    
    private static void runTestCycle() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n🧪 Запуск тестового циклу з кастомним таймінгом...");
        System.out.print("Введіть затримку перед стартом (сек): ");
        int startDelay = readInt(scanner, 2);
        System.out.print("Введіть затримку між парсингом і ранковим постингом (сек): ");
        int morningDelay = readInt(scanner, 2);
        System.out.print("Введіть затримку між кожним 'щогодинним' постингом (сек): ");
        int hourlyDelay = readInt(scanner, 2);
        System.out.print("Введіть кількість ітерацій 'щогодинного' постингу: ");
        int hourlyIterations = readInt(scanner, 3);
        AutoPostingScheduler scheduler = new AutoPostingScheduler();
        scheduler.runFullTestCycle(startDelay, morningDelay, hourlyDelay, hourlyIterations);
    }
    
    private static int readInt(Scanner scanner, int defaultValue) {
        String input = scanner.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static void printUsage() {
        System.out.println("\n📖 Використання:");
        System.out.println("  java -jar DimRiaParser.jar [команда]");
        System.out.println("\n📋 Доступні команди:");
        System.out.println("  parse  - Парсинг оголошень");
        System.out.println("  post   - Постинг оголошень");
        System.out.println("  auto   - Автоматичний режим");
        System.out.println("  test   - Тестовий постинг");
        System.out.println("  testfull - Повний тестовий режим");
        System.out.println("  testcycle - Тестовий цикл з кастомним таймінгом");
        System.out.println("\n💡 Без аргументів запускається інтерактивний режим");
    }
}