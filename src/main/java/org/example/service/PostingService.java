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
        
        // Отримуємо список всіх міст
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        List<Apartment> allApartments = new ArrayList<>();
        
        // Збираємо квартири з усіх таблиць
        for (org.example.config.CityConfig.City city : cities) {
            List<Apartment> cityApartments = databaseManager.getUnpostedApartments(city.dbTable, 2);
            allApartments.addAll(cityApartments);
        }
        
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
        
        // Отримуємо список всіх міст
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        List<Apartment> recentApartments = new ArrayList<>();
        
        // Спочатку пробуємо нові оголошення останньої години з усіх таблиць
        for (org.example.config.CityConfig.City city : cities) {
            List<Apartment> cityRecent = databaseManager.getUnpostedApartmentsFromLastHour(city.dbTable, 5);
            recentApartments.addAll(cityRecent);
        }
        
        if (recentApartments != null && !recentApartments.isEmpty()) {
            if (verbose) {
                System.out.println("🆕 Знайдено " + recentApartments.size() + " нових оголошень останньої години");
            }
            return postSmart(recentApartments);
        } else {
            // Якщо нових немає, беремо всі неопубліковані (без фільтра за часом)
            if (verbose) {
                System.out.println("📅 Використовуємо всі неопубліковані оголошення (нових немає)");
            }
            return postMorningApartments();
        }
    }
    
    /**
     * Позначає квартиру як опубліковану
     */
    public void markAsPublished(Apartment apartment) {
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
        // Отримуємо список всіх міст з конфігурації
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        
        // Перевіряємо квартиру в кожній таблиці
        for (org.example.config.CityConfig.City city : cities) {
            Optional<Apartment> foundApartment = databaseManager.getApartmentById(city.dbTable, apartment.getId());
            if (foundApartment.isPresent()) {
                return city.dbTable;
            }
        }
        
        return "Apartments_Lviv";
    }
    
    public boolean testTelegramConnection() {
        return telegramService.testConnection();
    }
    
    public void publishPostsForCity(String tableName, int postsCount) {
        System.out.println("\nПублікація постів для " + tableName + "...");
        
        List<Apartment> unpostedApartments = databaseManager.getUnpostedApartments(tableName, postsCount);
        
        if (unpostedApartments.isEmpty()) {
            System.out.println("Немає неопублікованих квартир для " + tableName);
            return;
        }
        
        int publishedCount = 0;
        
        for (Apartment apartment : unpostedApartments) {
            if (apartment.getPhotoPaths() == null || apartment.getPhotoPaths().isEmpty()) {
                if (verbose) {
                    System.out.println("Квартира " + apartment.getId() + " без фотографій - пропускаємо");
                }
                continue;
            }
            
            boolean success = telegramService.sendToBothChannels(apartment);
            
            if (success) {
                databaseManager.markAsPosted(tableName, apartment.getId());
                publishedCount++;
                
                if (verbose) {
                    System.out.println("Опубліковано квартиру " + apartment.getId() + " в " + tableName);
                }
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                System.err.println("Не вдалося опублікувати квартиру " + apartment.getId());
            }
        }
        
        System.out.println("Опубліковано " + publishedCount + " з " + unpostedApartments.size() + " квартир для " + tableName);
    }
    
    public void publishPostsForAllCitiesWithSmartLogic(int postsPerCity) {
        System.out.println("Публікація постів для всіх міст з розумною логікою...");
        
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        
        for (org.example.config.CityConfig.City city : cities) {
            publishPostsForCityWithSmartLogic(city.dbTable, postsPerCity);
        }
    }
    
    public void publishPostsForCityWithSmartLogic(String tableName, int postsCount) {
        System.out.println("\nПублікація постів для " + tableName + " з розумною логікою...");
        
        List<Apartment> newApartments = databaseManager.getUnpostedApartmentsFromLastHour(tableName, postsCount);
        
        if (!newApartments.isEmpty()) {
            System.out.println("Знайдено " + newApartments.size() + " нових квартир (остання година)");
            publishApartmentsList(tableName, newApartments);
        } else {
            System.out.println("Нових квартир немає, беремо всі неопубліковані");
            List<Apartment> allUnpostedApartments = databaseManager.getUnpostedApartments(tableName, postsCount);
            
            if (!allUnpostedApartments.isEmpty()) {
                publishApartmentsList(tableName, allUnpostedApartments);
            } else {
                System.out.println("Немає неопублікованих квартир для " + tableName);
            }
        }
    }
    
    private void publishApartmentsList(String tableName, List<Apartment> apartments) {
        int publishedCount = 0;
        
        for (Apartment apartment : apartments) {
            if (apartment.getPhotoPaths() == null || apartment.getPhotoPaths().isEmpty()) {
                if (verbose) {
                    System.out.println("Квартира " + apartment.getId() + " без фотографій - пропускаємо");
                }
                continue;
            }
            
            boolean success = telegramService.sendToBothChannels(apartment);
            
            if (success) {
                databaseManager.markAsPosted(tableName, apartment.getId());
                publishedCount++;
                
                if (verbose) {
                    System.out.println("Опубліковано квартиру " + apartment.getId() + " в " + tableName);
                }
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                System.err.println("Не вдалося опублікувати квартиру " + apartment.getId());
            }
        }
        
        System.out.println("Опубліковано " + publishedCount + " з " + apartments.size() + " квартир для " + tableName);
    }
    public void publishPostsForAllCities(int postsPerCity) {
        System.out.println("🌍 Публікація постів для всіх міст...");
        
        // Отримуємо список всіх міст
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        
        // Публікуємо для кожного міста
        for (org.example.config.CityConfig.City city : cities) {
            publishPostsForCity(city.dbTable, postsPerCity);
        }
    }
    
    /**
     * Отримує статистику по містах
     */
    public void printStatistics() {
        System.out.println("\n📊 Статистика по містах:");
        
        // Отримуємо список всіх міст
        List<org.example.config.CityConfig.City> cities = org.example.config.CityConfig.getCities();
        
        for (org.example.config.CityConfig.City city : cities) {
            List<Apartment> cityApartments = databaseManager.getUnpostedApartments(city.dbTable, 1000);
            System.out.println("🏙 " + city.name + ": " + cityApartments.size() + " неопублікованих квартир");
            
            if (!cityApartments.isEmpty()) {
                Apartment newest = cityApartments.get(0);
                System.out.println("   Найновіша квартира: " + newest.getId() + " (" + 
                                  formatDate(newest.getCreatedAt()) + ")");
            }
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

    /**
     * Постинг з ранкових оголошень для одного міста (9:00)
     */
    public boolean postMorningApartmentsForCity(String tableName, String channel1, String channel2) {
        if (verbose) {
            System.out.println("🌅 Починаємо постинг ранкових оголошень для таблиці: " + tableName);
        }
        
        // Беремо лише 2 найновіших квартири
        List<Apartment> apartments = databaseManager.getUnpostedApartments(tableName, 2);
        
        if (apartments.isEmpty()) {
            return false;
        }
        
        apartments.sort((a1, a2) -> {
            if (a1.getCreatedAt() == null && a2.getCreatedAt() == null) return 0;
            if (a1.getCreatedAt() == null) return 1;
            if (a2.getCreatedAt() == null) return -1;
            return a2.getCreatedAt().compareTo(a1.getCreatedAt());
        });
        Apartment apt1 = apartments.size() > 0 ? apartments.get(0) : null;
        Apartment apt2 = apartments.size() > 1 ? apartments.get(1) : null;
        
        // Логування попереджень
        if (channel1 == null || channel1.isEmpty() || channel2 == null || channel2.isEmpty()) {
            logWarning("[WARN] Для таблиці " + tableName + " не вказано обидва канали. Канал1: '" + channel1 + "', Канал2: '" + channel2 + "'");
        }
        
        boolean success = false;
        if (apt1 != null) success |= telegramService.sendApartmentPost(apt1, channel1);
        if (apt2 != null) success |= telegramService.sendApartmentPost(apt2, channel2);
        
        if (success) {
            if (apt1 != null) markAsPublished(apt1);
            if (apt2 != null) markAsPublished(apt2);
        }
        return success;
    }

    /**
     * Постинг для всіх міст (ранковий)
     */
    public void postMorningApartmentsForAllCities(List<org.example.config.CityConfig.City> cities) {
        for (org.example.config.CityConfig.City city : cities) {
            postMorningApartmentsForCity(city.dbTable, city.channel1, city.channel2);
        }
    }

    /**
     * Логування попереджень у файл warnings.log
     */
    public void logWarning(String message) {
        System.out.println(message);
        try (java.io.FileWriter fw = new java.io.FileWriter("warnings.log", true)) {
            fw.write(java.time.LocalDateTime.now() + " " + message + "\n");
        } catch (Exception e) {
            System.err.println("[LOG] Не вдалося записати у warnings.log: " + e.getMessage());
        }
    }
} 