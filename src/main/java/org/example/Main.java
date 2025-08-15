package org.example;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.scheduler.AutoPostingScheduler;
import org.example.service.PostingService;
import org.example.service.RiaParserService;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.println("🏠 DimRiaParser - Парсер оголошень з dom.ria.com");
        System.out.println("=".repeat(60));
        
        if (!validateConfiguration()) {
            System.exit(1);
        }
        
        // Перевіряємо завантаження міст
        System.out.println("🏙️  Перевірка завантаження міст...");
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        System.out.println("✅ Завантажено міст: " + cities.size());
        if (cities.isEmpty()) {
            System.err.println("❌ ПОМИЛКА: Не знайдено жодного міста в конфігурації!");
            System.err.println("❌ Перевірте файл config.properties");
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
        System.out.println("⚙️  Перевірка конфігурації...");
        
        String chromeDriverPath = AppConfig.getChromeDriverPath();
        if (!new java.io.File(chromeDriverPath).exists()) {
            System.err.println("❌ ChromeDriver не знайдено: " + chromeDriverPath);
            return false;
        }
        
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
        System.out.println("🗄️  Ініціалізація бази даних...");
        
        DatabaseManager dbManager = DatabaseManager.getInstance();
        
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        System.out.println("🏙️  Знайдено " + cities.size() + " міст для ініціалізації БД");
        
        for (org.example.config.CityConfig.City city : cities) {
            System.out.println("📋 Створення таблиці для міста: " + city.name + " (" + city.dbTable + ")");
            dbManager.createTable(city.dbTable);
        }
        
        System.out.println("✅ База даних ініціалізована");
        System.out.println("📸 Підтримка 10 фото на оголошення активована");
    }
    
    private static void handleCommandLineArgs(String[] args) {
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "parse":
                System.out.println("🔍 Запуск парсингу...");
                RiaParserService parser = new RiaParserService();
                parser.parseApartmentsForAllCities();
                break;
                
            case "post":
                System.out.println("📤 Запуск постингу...");
                PostingService postingService = new PostingService();
                postingService.postMorningApartmentsForAllCities(org.example.config.CityConfig.getCities());
                break;
                
            case "auto":
                System.out.println("🚀 Запуск автоматичного режиму (з 8:00)...");
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
                System.out.println("⚡ Запуск автоматичного режиму (з поточного моменту)...");
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
                System.out.println("🧪 Запуск тестового автоматичного режиму (швидкий цикл)...");
                AutoPostingScheduler testScheduler = new AutoPostingScheduler();
                testScheduler.startTestScheduledPosting();
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
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🏠 DimRiaParser - Меню");
            System.out.println("=".repeat(60));
            System.out.println("1. 🔍 Парсинг оголошень (ручний)");
            System.out.println("2. 📤 Постинг оголошень (ручний)");
            System.out.println("3. 🚀 Автоматичний режим (з 8:00)");
            System.out.println("4. ⚡ Автоматичний режим (з поточного моменту)");
            System.out.println("5. 🧪 Тестовий автоматичний режим (швидкий цикл)");
            System.out.println("6. ❌ Вихід");
            System.out.println("=".repeat(60));
            System.out.println("📅 Розклад автоматичного режиму:");
            System.out.println("   🌅 8:00 - Очистка БД + Парсинг");
            System.out.println("   🕙 10:00-22:00 - Щогодинний постинг + Допарсинг");
            System.out.println("   🌙 22:00-8:00 - Очікування до наступного дня");
            System.out.println("=".repeat(60));
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
                    runTestAutoMode();
                    break;
                    
                case "6":
                    System.out.println("👋 До побачення!");
                    return;
                    
                default:
                    System.out.println("❌ Невірний вибір. Спробуйте ще раз.");
                    break;
            }
        }
    }
    
    private static void runParsing() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("🔍 ЗАПУСК ПАРСИНГУ ОГОЛОШЕНЬ");
        System.out.println("=".repeat(50));
        
        try {
            List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
            System.out.println("🏙️  Кількість міст для парсингу: " + cities.size());
            
            if (cities.isEmpty()) {
                System.err.println("❌ ПОМИЛКА: Немає міст для парсингу!");
                return;
            }
            
            System.out.println("🚀 Починаємо парсинг...");
            RiaParserService parser = new RiaParserService();
            parser.parseApartmentsForAllCities();
            System.out.println("✅ Парсинг завершено успішно!");
        } catch (Exception e) {
            System.err.println("❌ Помилка парсингу: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void runPosting() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📤 ЗАПУСК ПОСТИНГУ ОГОЛОШЕНЬ");
        System.out.println("=".repeat(50));
        
        try {
            System.out.println("🚀 Починаємо постинг...");
            PostingService postingService = new PostingService();
            postingService.postMorningApartmentsForAllCities(org.example.config.CityConfig.getCities());
            System.out.println("✅ Постинг завершено успішно!");
        } catch (Exception e) {
            System.err.println("❌ Помилка постингу: " + e.getMessage());
        }
    }
    
    private static void runAutoMode() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🚀 ЗАПУСК АВТОМАТИЧНОГО РЕЖИМУ (з 8:00)");
        System.out.println("=".repeat(60));
        System.out.println("📅 Розклад:");
        System.out.println("   🌅 8:00 - Очистка БД + Парсинг");
        System.out.println("   🕙 10:00-22:00 - Щогодинний постинг + Допарсинг");
        System.out.println("   🌙 22:00-8:00 - Очікування до наступного дня");
        System.out.println("=".repeat(60));
        System.out.println("⚠️  Для зупинки натисніть Ctrl+C");
        System.out.println("🔄 Програма працюватиме безперервно кожен день!");
        System.out.println("=".repeat(60));
        
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
        System.out.println("\n" + "=".repeat(60));
        System.out.println("⚡ ЗАПУСК АВТОМАТИЧНОГО РЕЖИМУ (з поточного моменту)");
        System.out.println("=".repeat(60));
        System.out.println("📅 Розклад:");
        System.out.println("   🔍 Одразу - Парсинг поточних оголошень");
        System.out.println("   🕙 10:00-22:00 - Щогодинний постинг + Допарсинг");
        System.out.println("   🌙 22:00-8:00 - Очікування до наступного дня");
        System.out.println("   🌅 8:00 - Очистка БД + Парсинг (наступний день)");
        System.out.println("=".repeat(60));
        System.out.println("⚠️  Для зупинки натисніть Ctrl+C");
        System.out.println("🔄 Програма працюватиме безперервно кожен день!");
        System.out.println("=".repeat(60));
        
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
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🧪 ЗАПУСК ТЕСТОВОГО АВТОМАТИЧНОГО РЕЖИМУ");
        System.out.println("=".repeat(60));
        System.out.println("📅 Тестовий розклад (швидкий цикл):");
        System.out.println("   1️⃣  Очистка БД + Парсинг (як о 8:00)");
        System.out.println("   2️⃣  3 ітерації: Постинг → Парсинг → 10 сек очікування");
        System.out.println("   3️⃣  Тест завершено");
        System.out.println("=".repeat(60));
        System.out.println("ℹ️  Це тестовий режим для перевірки логіки");
        System.out.println("=".repeat(60));
        
        AutoPostingScheduler scheduler = new AutoPostingScheduler();
        scheduler.startTestScheduledPosting();
    }
    
    private static void printUsage() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🏠 DimRiaParser - Використання");
        System.out.println("=".repeat(70));
        System.out.println("Синтаксис:");
        System.out.println("  java -jar DimRiaParser.jar [команда]");
        System.out.println("\n📋 Доступні команди:");
        System.out.println("  🔍 parse   - Парсинг оголошень (ручний)");
        System.out.println("  📤 post    - Постинг оголошень (ручний)");
        System.out.println("  🚀 auto    - Автоматичний режим (з 8:00)");
        System.out.println("  ⚡ autonow - Автоматичний режим (з поточного моменту)");
        System.out.println("  🧪 test    - Тестовий автоматичний режим (швидкий цикл)");
        System.out.println("\n📅 Розклад автоматичного режиму:");
        System.out.println("   🌅 8:00 - Очистка БД + Парсинг");
        System.out.println("   🕙 10:00-22:00 - Щогодинний постинг + Допарсинг");
        System.out.println("   🌙 22:00-8:00 - Очікування до наступного дня");
        System.out.println("\n💡 Приклади:");
        System.out.println("  java -jar DimRiaParser.jar parse    # Тільки парсинг");
        System.out.println("  java -jar DimRiaParser.jar auto     # Автоматичний режим");
        System.out.println("  java -jar DimRiaParser.jar          # Інтерактивне меню");
        System.out.println("=".repeat(70));
        System.out.println("ℹ️  Без аргументів запускається інтерактивне меню");
        System.out.println("=".repeat(70));
    }
}