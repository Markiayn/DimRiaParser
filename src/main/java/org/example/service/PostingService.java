package org.example.service;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.model.Apartment;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

public class PostingService {
    private final DatabaseManager databaseManager;
    private final TelegramService telegramService;
    private final boolean verbose;
    
    public PostingService() {
        this.databaseManager = DatabaseManager.getInstance();
        this.telegramService = new TelegramService();
        this.verbose = AppConfig.isVerbose();
    }
    
    /**
     * Розумна логіка постингу: відправляє різні оголошення в різні канали
     */
    public boolean postSmart(List<Apartment> apartments) {
        if (apartments == null || apartments.isEmpty()) {
            if (verbose) {
                System.out.println("⚠️ Немає квартир для постингу");
            }
            return false;
        }
        
        // Фільтруємо квартири з фото
        List<Apartment> apartmentsWithPhotos = apartments.stream()
                .filter(apt -> apt.getPhotoPaths() != null && !apt.getPhotoPaths().isEmpty())
                .toList();
        
        if (apartmentsWithPhotos.isEmpty()) {
            if (verbose) {
                System.out.println("⚠️ Немає квартир з фото для постингу");
            }
            return false;
        }
        
        // Беремо дві найновіші квартири для різних каналів
        Apartment apartment1 = apartmentsWithPhotos.get(0);
        Apartment apartment2 = apartmentsWithPhotos.size() > 1 ? apartmentsWithPhotos.get(1) : null;
        
        if (verbose) {
            System.out.println("📤 Відправляємо квартиру " + apartment1.getId() + " в канал 1");
            if (apartment2 != null) {
                System.out.println("📤 Відправляємо квартиру " + apartment2.getId() + " в канал 2");
            }
        }
        
        // Відправляємо різні квартири в різні канали
        boolean success = telegramService.sendDifferentApartmentsToChannels(apartment1, apartment2);
        
        if (success) {
            // Позначаємо квартири як опубліковані
            markAsPublished(apartment1);
            if (apartment2 != null) {
                markAsPublished(apartment2);
            }
            
            if (verbose) {
                System.out.println("✅ Успішно опубліковано " + (apartment2 != null ? "2" : "1") + " оголошення");
            }
        }
        
        return success;
    }
    
    /**
     * Постинг з ранкових оголошень (9:00)
     */
    public boolean postMorningApartments() {
        if (verbose) {
            System.out.println("🌅 Починаємо постинг ранкових оголошень...");
        }
        // Отримуємо квартири з обох таблиць за останні 24 години
        List<Apartment> lvivApartments = databaseManager.getUnpostedApartmentsFromLast24Hours("Apartments_Lviv", 2);
        List<Apartment> ivanoFrankivskApartments = databaseManager.getUnpostedApartmentsFromLast24Hours("Apartments_IvanoFrankivsk", 2);
        // Об'єднуємо списки
        List<Apartment> allApartments = new ArrayList<>();
        allApartments.addAll(lvivApartments);
        allApartments.addAll(ivanoFrankivskApartments);
        // Сортуємо за датою створення (найновіші спочатку)
        allApartments.sort((a1, a2) -> {
            if (a1.getCreatedAt() == null && a2.getCreatedAt() == null) return 0;
            if (a1.getCreatedAt() == null) return 1;
            if (a2.getCreatedAt() == null) return -1;
            return a2.getCreatedAt().compareTo(a1.getCreatedAt());
        });
        // Беремо лише 2 найновіших
        if (allApartments.size() > 2) {
            allApartments = allApartments.subList(0, 2);
        }
        return postSmart(allApartments);
    }
    
    /**
     * Розумний постинг з нових оголошень останньої години або ранкових
     */
    public boolean postHourlyApartments() {
        if (verbose) {
            System.out.println("⏰ Починаємо щогодинний постинг...");
        }
        
        // Спочатку пробуємо нові оголошення останньої години з обох таблиць
        List<Apartment> lvivRecent = databaseManager.getUnpostedApartmentsFromLastHour("Apartments_Lviv", 5);
        List<Apartment> ivanoFrankivskRecent = databaseManager.getUnpostedApartmentsFromLastHour("Apartments_IvanoFrankivsk", 5);
        
        List<Apartment> recentApartments = new ArrayList<>();
        recentApartments.addAll(lvivRecent);
        recentApartments.addAll(ivanoFrankivskRecent);
        
        if (recentApartments != null && !recentApartments.isEmpty()) {
            if (verbose) {
                System.out.println("🆕 Знайдено " + recentApartments.size() + " нових оголошень останньої години");
            }
            return postSmart(recentApartments);
        } else {
            // Якщо нових немає, беремо з ранкових
            if (verbose) {
                System.out.println("📅 Використовуємо ранкові оголошення (нових немає)");
            }
            return postMorningApartments();
        }
    }
    
    /**
     * Позначає квартиру як опубліковану
     */
    private void markAsPublished(Apartment apartment) {
        try {
            // Визначаємо в якій таблиці знаходиться квартира
            String tableName = determineTableName(apartment);
            databaseManager.markAsPosted(tableName, apartment.getId());
            if (verbose) {
                System.out.println("✅ Квартира " + apartment.getId() + " позначена як опублікована в таблиці " + tableName);
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка позначення квартири як опублікованої: " + e.getMessage());
        }
    }
    
    /**
     * Визначає в якій таблиці знаходиться квартира
     */
    private String determineTableName(Apartment apartment) {
        // Простий спосіб - спробуємо знайти квартиру в обох таблицях
        Optional<Apartment> lvivApartment = databaseManager.getApartmentById("Apartments_Lviv", apartment.getId());
        if (lvivApartment.isPresent()) {
            return "Apartments_Lviv";
        }
        
        Optional<Apartment> ivanoFrankivskApartment = databaseManager.getApartmentById("Apartments_IvanoFrankivsk", apartment.getId());
        if (ivanoFrankivskApartment.isPresent()) {
            return "Apartments_IvanoFrankivsk";
        }
        
        // Якщо не знайдено, повертаємо за замовчуванням
        return "Apartments_Lviv";
    }
    
    /**
     * Тестує підключення до Telegram
     */
    public boolean testTelegramConnection() {
        return telegramService.testConnection();
    }
    
    /**
     * Відправляє тестове повідомлення
     */
    public boolean sendTestMessage() {
        Apartment testApartment = new Apartment();
        testApartment.setId(999999);
        testApartment.setDescription("Тестове оголошення");
        testApartment.setAddress("Тестова адреса");
        testApartment.setPrice(10000);
        testApartment.setFloor(5);
        testApartment.setFloorsCount(9);
        testApartment.setRooms(2);
        testApartment.setArea(50);
        testApartment.setPhone("+380991234567");
        
        return telegramService.sendToBothChannels(testApartment);
    }
    
    /**
     * Тестовий режим: парсинг + постинг
     */
    public boolean runTestMode() {
        System.out.println("🧪 Запуск тестового режиму (парсинг + постинг)...");
        
        try {
            // 1. Тестовий парсинг
            System.out.println("🔄 Крок 1: Тестовий парсинг...");
            RiaParserService parser = new RiaParserService();
            parser.parseTestApartments();
            
            // 2. Тестовий постинг
            System.out.println("📤 Крок 2: Тестовий постинг...");
            boolean success = postMorningApartments();
            
            if (success) {
                System.out.println("✅ Тестовий режим завершено успішно!");
                return true;
            } else {
                System.out.println("⚠️ Тестовий режим завершено, але постинг не вдався");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Помилка в тестовому режимі: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Публікує пости для вказаного міста
     */
    public void publishPostsForCity(String tableName, int postsCount) {
        System.out.println("\n📤 Публікація постів для " + tableName + "...");
        
        // Отримуємо неопубліковані квартири
        List<Apartment> unpostedApartments = databaseManager.getUnpostedApartments(tableName, postsCount);
        
        if (unpostedApartments.isEmpty()) {
            System.out.println("⚠️ Немає неопублікованих квартир для " + tableName);
            return;
        }
        
        int publishedCount = 0;
        
        for (Apartment apartment : unpostedApartments) {
            // Перевіряємо чи є фотографії
            if (apartment.getPhotoPaths() == null || apartment.getPhotoPaths().isEmpty()) {
                if (verbose) {
                    System.out.println("⚠️ Квартира " + apartment.getId() + " без фотографій - пропускаємо");
                }
                continue;
            }
            
            // Відправляємо в обидва канали
            boolean success = telegramService.sendToBothChannels(apartment);
            
            if (success) {
                // Позначаємо як опубліковану
                databaseManager.markAsPosted(tableName, apartment.getId());
                publishedCount++;
                
                if (verbose) {
                    System.out.println("✅ Опубліковано квартиру " + apartment.getId() + " в " + tableName);
                }
                
                // Затримка між постами
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                System.err.println("❌ Не вдалося опублікувати квартиру " + apartment.getId());
            }
        }
        
        System.out.println("📊 Опубліковано " + publishedCount + " з " + unpostedApartments.size() + " квартир для " + tableName);
    }
    
    /**
     * Публікує пости для всіх міст з розумною логікою
     * Спочатку шукає нові пости (остання година), якщо нема - беремо зранку
     */
    public void publishPostsForAllCitiesWithSmartLogic(int postsPerCity) {
        System.out.println("🌍 Публікація постів для всіх міст з розумною логікою...");
        
        // Львів
        publishPostsForCityWithSmartLogic("Apartments_Lviv", postsPerCity);
        
        // Івано-Франківськ
        publishPostsForCityWithSmartLogic("Apartments_IvanoFrankivsk", postsPerCity);
    }
    
    /**
     * Публікує пости для вказаного міста з розумною логікою
     */
    public void publishPostsForCityWithSmartLogic(String tableName, int postsCount) {
        System.out.println("\n📤 Публікація постів для " + tableName + " з розумною логікою...");
        
        // Спочатку шукаємо нові пости (остання година)
        List<Apartment> newApartments = databaseManager.getUnpostedApartmentsFromLastHour(tableName, postsCount);
        
        if (!newApartments.isEmpty()) {
            System.out.println("🆕 Знайдено " + newApartments.size() + " нових квартир (остання година)");
            publishApartmentsList(tableName, newApartments);
        } else {
            // Якщо нових немає, беремо зранку
            System.out.println("📅 Нових квартир немає, беремо зранку");
            List<Apartment> morningApartments = databaseManager.getUnpostedApartments(tableName, postsCount);
            
            if (!morningApartments.isEmpty()) {
                publishApartmentsList(tableName, morningApartments);
            } else {
                System.out.println("⚠️ Немає неопублікованих квартир для " + tableName);
            }
        }
    }
    
    /**
     * Публікує список квартир
     */
    private void publishApartmentsList(String tableName, List<Apartment> apartments) {
        int publishedCount = 0;
        
        for (Apartment apartment : apartments) {
            // Перевіряємо чи є фотографії
            if (apartment.getPhotoPaths() == null || apartment.getPhotoPaths().isEmpty()) {
                if (verbose) {
                    System.out.println("⚠️ Квартира " + apartment.getId() + " без фотографій - пропускаємо");
                }
                continue;
            }
            
            // Відправляємо в обидва канали
            boolean success = telegramService.sendToBothChannels(apartment);
            
            if (success) {
                // Позначаємо як опубліковану
                databaseManager.markAsPosted(tableName, apartment.getId());
                publishedCount++;
                
                if (verbose) {
                    System.out.println("✅ Опубліковано квартиру " + apartment.getId() + " в " + tableName);
                }
                
                // Затримка між постами
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                System.err.println("❌ Не вдалося опублікувати квартиру " + apartment.getId());
            }
        }
        
        System.out.println("📊 Опубліковано " + publishedCount + " з " + apartments.size() + " квартир для " + tableName);
    }
    
    /**
     * Публікує пости для всіх міст
     */
    public void publishPostsForAllCities(int postsPerCity) {
        System.out.println("🌍 Публікація постів для всіх міст...");
        
        // Львів
        publishPostsForCity("Apartments_Lviv", postsPerCity);
        
        // Івано-Франківськ
        publishPostsForCity("Apartments_IvanoFrankivsk", postsPerCity);
    }
    
    /**
     * Отримує статистику по містах
     */
    public void printStatistics() {
        System.out.println("\n📊 Статистика по містах:");
        
        List<Apartment> lvivApartments = databaseManager.getUnpostedApartments("Apartments_Lviv", 1000);
        List<Apartment> frankivskApartments = databaseManager.getUnpostedApartments("Apartments_IvanoFrankivsk", 1000);
        
        System.out.println("🏙 Львівська область: " + lvivApartments.size() + " неопублікованих квартир");
        System.out.println("🏙 Івано-Франківська область: " + frankivskApartments.size() + " неопублікованих квартир");
        
        if (!lvivApartments.isEmpty()) {
            Apartment newestLviv = lvivApartments.get(0);
            System.out.println("   Найновіша квартира у Львові: " + newestLviv.getId() + " (" + 
                              formatDate(newestLviv.getCreatedAt()) + ")");
        }
        
        if (!frankivskApartments.isEmpty()) {
            Apartment newestFrankivsk = frankivskApartments.get(0);
            System.out.println("   Найновіша квартира у Івано-Франківську: " + newestFrankivsk.getId() + " (" + 
                              formatDate(newestFrankivsk.getCreatedAt()) + ")");
        }
    }
    
    /**
     * Форматує дату для виведення
     */
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "Не вказано";
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm");
        return dateTime.format(formatter);
    }
} 