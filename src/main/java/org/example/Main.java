package org.example;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.service.RiaParserService;
import org.example.utils.FileUtils;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("🏠 Запуск парсера нерухомості Dom.ria");
        System.out.println("=====================================");

        // Ініціалізуємо сервіси
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        RiaParserService parserService = new RiaParserService();

        // Очищаємо старі дані
        System.out.println("\n🧹 Очищення старих даних...");
        databaseManager.deleteAllFromTable("Apartments_Lviv");
        databaseManager.deleteAllFromTable("Apartments_IvanoFrankivsk");
        FileUtils.deleteAllPhotos(AppConfig.getPhotosDirectory());

        // Парсимо Львів
        System.out.println("\n🏙 Парсинг Львівської області...");
        parserService.parseApartments(
                "Apartments_Lviv",
                5,        // область (Львівська)
                null,     // місто (null якщо не потрібно)
                2,        // тип нерухомості (2 = квартира)
                3,        // тип операції (3 = оренда)
                AppConfig.getHoursLimit(),
                AppConfig.getMaxPages(),
                AppConfig.getMinRooms(),
                AppConfig.getMinArea(),
                AppConfig.getMaxPhotosPerApartment()
        );

        // Парсимо Івано-Франківськ
        System.out.println("\n🏙 Парсинг Івано-Франківської області...");
        parserService.parseApartments(
                "Apartments_IvanoFrankivsk",
                15,       // область (Івано-Франківська)
                null,     // місто (null якщо не потрібно)
                2,        // тип нерухомості (2 = квартира)
                3,        // тип операції (3 = оренда)
                AppConfig.getHoursLimit(),
                AppConfig.getMaxPages(),
                AppConfig.getMinRooms(),
                AppConfig.getMinArea(),
                AppConfig.getMaxPhotosPerApartment()
        );

        System.out.println("\n✅ Парсинг завершено!");
        System.out.println("📊 Статистика:");
        System.out.println("   - Львівська область: " + databaseManager.getUnpostedApartments("Apartments_Lviv", 1000).size() + " квартир");
        System.out.println("   - Івано-Франківська область: " + databaseManager.getUnpostedApartments("Apartments_IvanoFrankivsk", 1000).size() + " квартир");
        System.out.println("   - Фотографії: " + FileUtils.getFileCount(AppConfig.getPhotosDirectory()) + " файлів (" + 
                          FileUtils.formatFileSize(FileUtils.getDirectorySize(AppConfig.getPhotosDirectory())) + ")");

        // Розкоментуйте для відправки в Telegram
        // TelegramPostDispatcher dispatcher = new TelegramPostDispatcher();
        // dispatcher.dispatchPosts(2); // або скільки хочете постів на місто
    }
}