package org.example.service;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.model.Apartment;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.json.*;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v143.network.Network;
import org.openqa.selenium.devtools.v143.network.model.Request;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class RiaParserService {
    private static final List<String> interceptedFxPhotos = Collections.synchronizedList(new ArrayList<>());
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DatabaseManager databaseManager;
    private final String photosDirectory;
    private final boolean verbose;

    // ✅ ЧОРНИЙ СПИСОК НОМЕРІВ У ТОМУ Ж ФОРМАТІ, ЯК ТИ ЙОГО ОТРИМУЄШ (напр. "(093) 612 09 93")
    // ДОДАЙ СЮДИ СВОЇ НОМЕРИ
    private static final Set<String> BLACKLIST = new HashSet<>(Set.of(
        // Приклади:
            "(097) 480 04 63",
            "(093) 007 01 85",
            "(066) 825 40 16",
            "(050) 416 90 05",
            "(095) 335 64 14",
            "(050) 264 64 13"
        // ...додай інші
    ));

    // Кеш для зберігання ID квартир, які вже перевірені на існування
    private final Set<Integer> checkedApartmentIds = Collections.synchronizedSet(new HashSet<>());

    public RiaParserService() {
        this.databaseManager = DatabaseManager.getInstance();
        this.photosDirectory = AppConfig.getPhotosDirectory();
        this.verbose = AppConfig.isVerbose();
    }

    /**
     * Очищує кеш перевірених ID квартир
     */
    public void clearCache() {
        checkedApartmentIds.clear();
        System.out.println("🧹 Кеш перевірених квартир очищено");
    }

    // Новий метод для ранкового парсингу з очищенням
    public void parseApartmentsForAllCitiesMorning() {
        // Очищуємо кеш перевірених квартир перед ранковим парсингом
        clearCache();

        org.example.utils.FileUtils.deleteAllPhotos(photosDirectory);
        for (org.example.config.CityConfig.City city : org.example.config.CityConfig.getCities()) {
            databaseManager.clearTable(city.dbTable);
            System.out.println("Парсинг міста: " + city.name + " (cityId=" + city.cityId + ", таблиця: " + city.dbTable + ", годин: " + city.hours + ")");
            parseApartments(
                city.dbTable,
                city.cityId,
                city.cityId, // Передаємо cityId замість null
                2,
                3,
                city.hours,
                AppConfig.getMaxPages(),
                AppConfig.getMinRooms(),
                AppConfig.getMinArea(),
                AppConfig.getMaxPhotosPerApartment()
            );
        }

        // Виводимо статистику після ранкового парсингу
        System.out.println("\n📊 СТАТИСТИКА ПІСЛЯ РАНКОВОГО ПАРСИНГУ:");
        databaseManager.printStatisticsForAllCities();
    }

    // Звичайний парсинг протягом дня — без очищення
    public void parseApartmentsForAllCities() {
        // Очищуємо кеш перевірених квартир перед парсингом
        clearCache();

        System.out.println("Починаємо парсинг для " + org.example.config.CityConfig.getCities().size() + " міст...");

        for (org.example.config.CityConfig.City city : org.example.config.CityConfig.getCities()) {
            System.out.println("\n🔍 Парсинг міста: " + city.name + " (cityId=" + city.cityId + ", таблиця: " + city.dbTable + ", годин: " + city.hours + ")");
            parseApartments(
                city.dbTable,
                city.cityId,
                city.cityId, // Передаємо cityId замість null
                2,
                3,
                city.hours,
                AppConfig.getMaxPages(),
                AppConfig.getMinRooms(),
                AppConfig.getMinArea(),
                AppConfig.getMaxPhotosPerApartment()
            );
            System.out.println("✅ Парсинг міста " + city.name + " завершено");
        }

        // Виводимо статистику після звичайного парсингу
        System.out.println("\n📊 СТАТИСТИКА ПІСЛЯ ПАРСИНГУ:");
        databaseManager.printStatisticsForAllCities();
    }

    public void parseApartments(String tableName, int regionId, Integer cityId,
                               int realtyType, int operationType, int hoursLimit,
                               int maxPages, int minRooms, double minArea, int maxPhotos) {

        // Очищуємо кеш перевірених квартир для кожного міста
        checkedApartmentIds.clear();

        System.out.println("📋 Параметри парсингу:");
        System.out.println("   Таблиця: " + tableName);
        System.out.println("   Регіон: " + regionId + ", Місто: " + (cityId != null ? cityId : "всі"));
        System.out.println("   Макс. сторінок: " + maxPages + ", Макс. фото: " + maxPhotos);
        System.out.println("   Фільтри: " + minRooms + "+ кімнат, " + minArea + "+ м², " + hoursLimit + " годин");

        databaseManager.createTable(tableName);

        System.setProperty("webdriver.chrome.driver", AppConfig.getChromeDriverPath());

        ChromeDriver driver = null;
        try {
            System.out.println("🚀 Запуск ChromeDriver...");
            driver = setupDriver();
            DevTools devTools = setupDevTools(driver);
            String[] hashHolder = setupHashListener(devTools);
            String[] phoneHolder = new String[1];

            setupPhotoInterceptor(devTools);
            System.out.println("✅ ChromeDriver готовий до роботи");

            ParserStats stats = new ParserStats();

            for (int page = 0; page < maxPages; page++) {
                System.out.println("📄 Обробка сторінки " + (page + 1) + " з " + maxPages);
                if (!parsePage(tableName, page, regionId, cityId, realtyType, operationType,
                             hoursLimit, minRooms, minArea, maxPhotos, driver, formatter,
                             hashHolder, phoneHolder, stats)) {
                    System.out.println("⏹ Зупинка парсингу (більше сторінок немає)");
                    break;
                }

                // Мінімальна затримка між сторінками
                if (page < maxPages - 1) { // Не чекаємо після останньої сторінки
                    try {
                        Thread.sleep(500); // 500 мс затримки між сторінками
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            System.out.println("📊 Результати парсингу:");
            stats.printSummary(hoursLimit);

        } catch (Exception e) {
            System.err.println("❌ Критична помилка при парсингу: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                System.out.println("🔒 Закриття ChromeDriver...");
                driver.quit();
            }
        }
    }

    private boolean parsePage(String tableName, int page, int regionId, Integer cityId,
                            int realtyType, int operationType, int hoursLimit, int minRooms,
                            double minArea, int maxPhotos, ChromeDriver driver,
                            DateTimeFormatter formatter, String[] hashHolder,
                            String[] phoneHolder, ParserStats stats) {

        try {
            String url = buildSearchUrl(page, regionId, cityId, realtyType, operationType);

            if (verbose) {
                System.out.println("🔗 URL: " + url);
            }

            System.out.println("📡 Отримання даних з API...");
            Connection.Response response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .timeout(8000) // 8 секунд таймаут для пошукового API
                    .execute();

            JSONObject searchResult = new JSONObject(response.body());
            JSONArray items = searchResult.optJSONArray("items");

            if (items == null || items.isEmpty()) {
                System.out.println("📭 На сторінці " + (page + 1) + " оголошень не знайдено");
                return false;
            }

            System.out.println("📋 Знайдено " + items.length() + " оголошень на сторінці " + (page + 1));
            stats.totalFound += items.length();

            int processedCount = 0;
            for (int i = 0; i < items.length(); i++) {
                int id = items.getInt(i);
                if (processApartment(tableName, id, driver, formatter, hashHolder,
                                   phoneHolder, hoursLimit, stats, minRooms, minArea, maxPhotos)) {
                    stats.shown++;
                    processedCount++;
                }

                // Мінімальна затримка між обробкою квартир
                try {
                    Thread.sleep(200); // 200 мс затримки для стабільності
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("✅ Оброблено " + processedCount + " з " + items.length() + " оголошень на сторінці " + (page + 1));
            return true;

        } catch (Exception e) {
            System.err.println("❌ Помилка при парсингу сторінки " + (page + 1) + ": " + e.getMessage());
            return false;
        }
    }

    private String buildSearchUrl(int page, int regionId, Integer cityId, int realtyType, int operationType) {
        StringBuilder url = new StringBuilder("https://dom.ria.com/node/searchEngine/v2/?")
                .append("addMoreRealty=false&excludeSold=1&category=1")
                .append("&realty_type=").append(realtyType)
                .append("&operation=").append(operationType)
                .append("&state_id=").append(regionId)
                .append("&city_id=").append(regionId) // Дублюємо state_id в city_id
                .append("&price_cur=1&wo_dupl=1&sort=created_at")
                .append("&firstIteraction=false&limit=20&type=list&client=searchV2");

        url.append("&page=").append(page);
        return url.toString();
    }

    private boolean processApartment(String tableName, int id, ChromeDriver driver,
                                   DateTimeFormatter formatter, String[] hashHolder,
                                   String[] phoneHolder, int hoursLimit, ParserStats stats,
                                   int minRooms, double minArea, int maxPhotos) {
        try {
            // Перевіряємо чи вже перевіряли цю квартиру в поточній сесії
            if (checkedApartmentIds.contains(id)) {
                if (verbose) {
                    System.out.println("⏭️ Квартира " + id + " вже перевірена в поточній сесії");
                }
                return false;
            }

            // Додаємо ID до кешу перевірених
            checkedApartmentIds.add(id);

            // Перевіряємо чи квартира вже існує в базі даних
            if (databaseManager.apartmentExists(tableName, id)) {
                if (verbose) {
                    System.out.println("⏭️ Квартира " + id + " вже існує в базі даних");
                }
                stats.skippedAlreadyExists++;
                return false;
            }

            // Отримуємо дані квартири
            JSONObject data = fetchApartmentData(id);
            if (data == null) return false;

            // Перевіряємо фільтри
            if (!passesFilters(data, formatter, hoursLimit, minRooms, minArea, stats)) {
                return false;
            }

            // Створюємо об'єкт квартири
            Apartment apartment = createApartmentFromData(data, id);

            // Завантажуємо фотографії
            downloadPhotos(apartment, driver, maxPhotos, data);

            // Отримуємо телефон
            String phone = fetchPhone(hashHolder, phoneHolder);
            apartment.setPhone(phone);

            // ✅ ЄДИНИЙ ЧЕК: якщо номер у чорному списку — пропускаємо оголошення
            if (phone != null && BLACKLIST.contains(phone)) {
                if (verbose) {
                    System.out.println("📵 Пропущено квартиру з номером із чорного списку: " + phone + " (ID: " + id + ")");
                }
                return false; // нічого не пишемо в БД
            }

            // Зберігаємо в базу даних
            databaseManager.insertApartment(tableName, apartment);

            if (verbose) {
                System.out.println("✅ Оброблено квартиру: " + apartment);
            }

            return true;

        } catch (Exception e) {
            if (verbose) {
                System.out.println("⛔️ Помилка при обробці ID " + id + ": " + e.getMessage());
            }
            return false;
        }
    }

    private JSONObject fetchApartmentData(int id) {
        try {
            String response = Jsoup.connect("https://dom.ria.com/realty/data/" + id + "?lang_id=4&key=")
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000) // 5 секунд таймаут
                    .execute().body();
            return new JSONObject(response);
        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка отримання даних для ID " + id + ": " + e.getMessage());
            }
            return null;
        }
    }

    private boolean passesFilters(JSONObject data, DateTimeFormatter formatter,
                                int hoursLimit, int minRooms, double minArea, ParserStats stats) {

        // Перевіряємо дату публікації
        String pubDateStr = data.optString("publishing_date");
        if (pubDateStr == null || pubDateStr.isEmpty()) {
            stats.filteredEmptyDate++;
            return false;
        }

        try {
            LocalDateTime published = LocalDateTime.parse(pubDateStr, formatter);
            if (Duration.between(published, LocalDateTime.now()).toHours() > hoursLimit) {
                stats.filteredTooOld++;
                return false;
            }
        } catch (Exception e) {
            stats.filteredEmptyDate++;
            return false;
        }

        // Перевіряємо кількість кімнат та площу
        int rooms = data.optInt("rooms_count");
        double area = data.optDouble("total_square_meters");
        if (rooms < minRooms || area < minArea) {
            return false;
        }

        // Перевіряємо наявність URL
        String beautifulUrl = data.optString("beautiful_url");
        if (beautifulUrl.isEmpty()) {
            stats.filteredNoUrl++;
            return false;
        }

        return true;
    }

    private Apartment createApartmentFromData(JSONObject data, int id) {
        String description = data.optString("description_uk");
        int price = data.optInt("price");
        int floor = data.optInt("floor");
        int floorsCount = data.optInt("floors_count");
        int rooms = data.optInt("rooms_count");
        double area = data.optDouble("total_square_meters");
        String street = data.optString("street_name_uk");
        String building = data.optString("building_number_str");
        String address = street + ", буд. " + building;
        String pubDateStr = data.optString("publishing_date");

        LocalDateTime createdAt = LocalDateTime.parse(pubDateStr, formatter);

        return new Apartment(id, description, address, price, null, floor, floorsCount, rooms, area, createdAt);
    }

    private void downloadPhotos(Apartment apartment, ChromeDriver driver, int maxPhotos, JSONObject data) {
        try {
            String beautifulUrl = data.optString("beautiful_url");
            if (beautifulUrl.isEmpty()) {
                return;
            }

            String fullUrl = "https://dom.ria.com/uk/" + beautifulUrl;
            driver.get(fullUrl);

            // Очищаємо попередні перехоплені фото
            interceptedFxPhotos.clear();

            // Натискаємо "Дивитися всі фото"
            try {
                WebElement showAllPhotosButton = driver.findElement(By.cssSelector("li[class*='photo-'] span.all-photos"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", showAllPhotosButton);
                if (verbose) System.out.println("🖼 Натиснуто 'Дивитися всі фото'");
                Thread.sleep(500); // Даємо час для завантаження галереї
            } catch (Exception e) {
                if (verbose) System.out.println("⚠️ Кнопка 'Дивитися всі фото' не знайдена");
            }

            // Прокручуємо фотографії для отримання 10 фото з покращеною логікою
            int photosFound = 0;
            int maxAttempts = 25; // Збільшено для більшої надійності
            int consecutiveFailures = 0; // Лічильник послідовних невдач
            int lastPhotoCount = 0; // Кількість фото на попередній ітерації

            // Спочатку чекаємо завантаження початкової галереї
            Thread.sleep(1000);

            for (int attempt = 0; attempt < maxAttempts && photosFound < maxPhotos && consecutiveFailures < 5; attempt++) {
                try {
                    // Перевіряємо скільки фото вже перехоплено
                    int currentPhotos = interceptedFxPhotos.size();

                    // Спробуємо різні селектори для кнопки "наступне фото"
                    WebElement nextButton = null;
                    try {
                        nextButton = driver.findElement(By.cssSelector("button.rotate-btn.rotate-arr-r"));
                    } catch (Exception e1) {
                        try {
                            nextButton = driver.findElement(By.cssSelector("button[class*='rotate'][class*='arr-r']"));
                        } catch (Exception e2) {
                            try {
                                nextButton = driver.findElement(By.cssSelector("button[aria-label*='наступн']"));
                            } catch (Exception e3) {
                                // Спробуємо знайти за текстом
                                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                                for (WebElement btn : buttons) {
                                    if (btn.getText().contains("→") || btn.getAttribute("aria-label") != null &&
                                        btn.getAttribute("aria-label").contains("наступн")) {
                                        nextButton = btn;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (nextButton != null && nextButton.isEnabled()) {
                        // Натискаємо кнопку "наступне фото"
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);

                        // Чекаємо завантаження нового фото
                        Thread.sleep(600);

                        // Перевіряємо чи додалося нове фото
                        if (interceptedFxPhotos.size() > currentPhotos) {
                            photosFound = interceptedFxPhotos.size();
                            consecutiveFailures = 0; // Скидаємо лічильник невдач
                            if (verbose) System.out.println("📸 Знайдено фото: " + photosFound + " (спроба " + (attempt + 1) + ")");
                        } else {
                            consecutiveFailures++;
                            if (verbose) System.out.println("⏳ Фото не знайдено, спроба " + (attempt + 1) + " (невдач підряд: " + consecutiveFailures + ")");
                        }
                    } else {
                        consecutiveFailures++;
                        if (verbose) System.out.println("⚠️ Кнопка 'наступне фото' не знайдена або неактивна");
                    }

                } catch (Exception e) {
                    consecutiveFailures++;
                    if (verbose) System.out.println("⚠️ Помилка на спробі " + (attempt + 1) + ": " + e.getMessage());

                    // Якщо багато помилок підряд, зупиняємося
                    if (consecutiveFailures >= 5) {
                        if (verbose) System.out.println("🏁 Зупинка через багато помилок підряд");
                        break;
                    }
                }
            }

            // Додаткове очікування для завершення завантаження
            Thread.sleep(500);

            if (verbose) {
                System.out.println("📊 Перехоплено фото: " + interceptedFxPhotos.size());
                System.out.println("🎯 Ціль: " + maxPhotos + " фото");
            }

            // Якщо знайдено мало фото, спробуємо ще раз з різними підходами
            if (interceptedFxPhotos.size() < 5 && maxPhotos > 5) {
                if (verbose) System.out.println("🔄 Мало фото знайдено (" + interceptedFxPhotos.size() + "), спробуємо альтернативні методи...");

                // Спроба 1: Прокрутка клавішами
                try {
                    if (verbose) System.out.println("⌨️ Спроба прокрутки клавішами...");
                    for (int keyAttempt = 0; keyAttempt < 10; keyAttempt++) {
                        int currentPhotos = interceptedFxPhotos.size();
                        driver.findElement(By.tagName("body")).sendKeys(Keys.ARROW_RIGHT);
                        Thread.sleep(400);

                        if (interceptedFxPhotos.size() > currentPhotos) {
                            if (verbose) System.out.println("📸 Клавішами знайдено фото: " + interceptedFxPhotos.size());
                        }
                    }
                } catch (Exception e) {
                    if (verbose) System.out.println("⚠️ Помилка прокрутки клавішами: " + e.getMessage());
                }

                // Спроба 2: Прокрутка мишею
                try {
                    if (verbose) System.out.println("🖱️ Спроба прокрутки мишею...");
                    WebElement gallery = driver.findElement(By.cssSelector(".gallery-container, .photo-gallery, [class*='gallery']"));
                    Actions actions = new Actions(driver);

                    for (int mouseAttempt = 0; mouseAttempt < 8; mouseAttempt++) {
                        int currentPhotos = interceptedFxPhotos.size();
                        actions.moveToElement(gallery).click().sendKeys(Keys.ARROW_RIGHT).perform();
                        Thread.sleep(500);

                        if (interceptedFxPhotos.size() > currentPhotos) {
                            if (verbose) System.out.println("📸 Мишею знайдено фото: " + interceptedFxPhotos.size());
                        }
                    }
                } catch (Exception e) {
                    if (verbose) System.out.println("⚠️ Помилка прокрутки мишею: " + e.getMessage());
                }
            }

            // Завантажуємо фотографії
            int counter = 1;
            for (String photoUrl : interceptedFxPhotos) {
                if (counter > maxPhotos) break;

                // Визначаємо якість з URL для назви файлу
                String quality = "unknown";
                if (photoUrl.endsWith("fx.webp")) quality = "fx";
                else if (photoUrl.endsWith("lg.webp")) quality = "lg";
                else if (photoUrl.endsWith("md.webp")) quality = "md";
                else if (photoUrl.endsWith("sm.webp")) quality = "sm";
                else if (photoUrl.endsWith("xs.webp")) quality = "xs";
                else if (photoUrl.endsWith("thumb.webp")) quality = "thumb";

                // Зберігаємо в кращому форматі - JPG замість WebP
                String photoFileName = photosDirectory + "/" + apartment.getId() + "_" + quality + "_" + counter + ".jpg";

                try (InputStream in = new URL(photoUrl).openStream()) {
                    Files.createDirectories(Paths.get(photosDirectory));
                    Files.copy(in, Paths.get(photoFileName), StandardCopyOption.REPLACE_EXISTING);
                    apartment.addPhotoPath(photoFileName);
                    counter++;
                    if (verbose) {
                        System.out.println("💾 Збережено фото " + counter + " якості " + quality);
                    }
                } catch (IOException e) {
                    if (verbose) {
                        System.err.println("⚠️ Помилка завантаження фото " + photoUrl + ": " + e.getMessage());
                    }
                }
            }

            if (verbose) {
                System.out.println("💾 Збережено фото: " + (counter - 1));
            }

            // Якщо браузерний спосіб не дав результатів, спробуємо через API
            if (apartment.getPhotoPaths().isEmpty() && !beautifulUrl.isEmpty()) {
                try {
                    downloadPhotosViaAPI(apartment, data, maxPhotos);
                    if (verbose) {
                        System.out.println("🔄 Спробовано завантажити фото через API");
                    }
                } catch (Exception e) {
                    if (verbose) {
                        System.err.println("⚠️ Помилка завантаження фото через API: " + e.getMessage());
                    }
                }
            }

            interceptedFxPhotos.clear();

        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка завантаження фотографій: " + e.getMessage());
            }
        }
    }

    private void downloadPhotosViaAPI(Apartment apartment, JSONObject data, int maxPhotos) {
        try {
            // Отримуємо фото через API
            JSONArray photos = data.optJSONArray("photos");
            if (photos != null && photos.length() > 0) {
                int counter = 1;
                for (int i = 0; i < Math.min(photos.length(), maxPhotos); i++) {
                    try {
                        JSONObject photo = photos.getJSONObject(i);
                        String photoUrl = photo.optString("url");

                        if (!photoUrl.isEmpty()) {
                            String photoFileName = photosDirectory + "/" + apartment.getId() + "_api_" + counter + ".jpg";

                            try (InputStream in = new URL(photoUrl).openStream()) {
                                Files.createDirectories(Paths.get(photosDirectory));
                                Files.copy(in, Paths.get(photoFileName), StandardCopyOption.REPLACE_EXISTING);
                                apartment.addPhotoPath(photoFileName);
                                counter++;
                            } catch (IOException e) {
                                if (verbose) {
                                    System.err.println("⚠️ Помилка завантаження API фото " + photoUrl + ": " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        if (verbose) {
                            System.err.println("⚠️ Помилка обробки API фото " + i + ": " + e.getMessage());
                        }
                    }
                }

                if (verbose) {
                    System.out.println("📸 Завантажено " + (counter - 1) + " фото через API");
                }
            }
        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка завантаження фото через API: " + e.getMessage());
            }
        }
    }

    private String fetchPhone(String[] hashHolder, String[] phoneHolder) {
        try {
            String hash = hashHolder[0];
            if (hash != null) {
                String apiUrl = "https://dom.ria.com/v1/api/realty/getOwnerAndAgencyData/" + hash + "?spa_final_page=true";
                JSONObject obj = new JSONObject(Jsoup.connect(apiUrl)
                        .ignoreContentType(true)
                        .userAgent("Mozilla/5.0")
                        .timeout(5000) // 5 секунд таймаут
                        .execute().body());

                try {
                    String phone = obj.getJSONObject("owner").getJSONArray("phones").getJSONObject(0).getString("phone_num");
                    phoneHolder[0] = phone;
                    if (verbose) System.out.println("📞 Номер телефону: " + phone);
                    return phone;
                } catch (Exception e) {
                    if (verbose) {
                        System.err.println("⚠️ Помилка парсингу телефону з JSON: " + e.getMessage());
                    }
                    return null;
                }
            } else {
                if (verbose) System.out.println("❌ Hash не перехоплено.");
                return null;
            }
        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка отримання телефону: " + e.getMessage());
            }
            return null;
        }
    }

    private void setupPhotoInterceptor(DevTools devTools) {
        devTools.addListener(Network.requestWillBeSent(), request -> {
            String url = request.getRequest().getUrl();
            // Фільтр для фото з різними варіантами якості
            if (url.contains("photosnew/dom/photo/") &&
                (url.endsWith("fx.webp") || url.endsWith("lg.webp") ||
                 url.endsWith("md.webp") || url.endsWith("sm.webp") ||
                 url.endsWith("xs.webp") || url.endsWith("thumb.webp"))) {

                // Видаляємо параметри з URL для унікальності
                String cleanUrl = url.split("\\?")[0];

                // Визначаємо якість фото з URL
                String quality = "unknown";
                if (url.endsWith("fx.webp")) quality = "fx";
                else if (url.endsWith("lg.webp")) quality = "lg";
                else if (url.endsWith("md.webp")) quality = "md";
                else if (url.endsWith("sm.webp")) quality = "sm";
                else if (url.endsWith("xs.webp")) quality = "xs";
                else if (url.endsWith("thumb.webp")) quality = "thumb";

                // Перевіряємо чи це не дублікат за базовим URL
                boolean isDuplicate = false;
                String baseUrl = cleanUrl.replaceAll("_(fx|lg|md|sm|xs|thumb)\\.webp$", "");

                for (String existingUrl : interceptedFxPhotos) {
                    String existingCleanUrl = existingUrl.split("\\?")[0];
                    String existingBaseUrl = existingCleanUrl.replaceAll("_(fx|lg|md|sm|xs|thumb)\\.webp$", "");
                    if (existingBaseUrl.equals(baseUrl)) {
                        // Якщо знайдено дублікат, замінюємо на кращу якість
                        String existingQuality = "unknown";
                        if (existingCleanUrl.endsWith("fx.webp")) existingQuality = "fx";
                        else if (existingCleanUrl.endsWith("lg.webp")) existingQuality = "lg";
                        else if (existingCleanUrl.endsWith("md.webp")) existingQuality = "md";
                        else if (existingCleanUrl.endsWith("sm.webp")) existingQuality = "sm";
                        else if (existingCleanUrl.endsWith("xs.webp")) existingQuality = "xs";
                        else if (existingCleanUrl.endsWith("thumb.webp")) existingQuality = "thumb";

                        // Порівнюємо якість (fx > lg > md > sm > xs > thumb)
                        if (isBetterQuality(quality, existingQuality)) {
                            interceptedFxPhotos.remove(existingUrl);
                            interceptedFxPhotos.add(url);
                            if (verbose) {
                                System.out.println("🔄 Замінено на кращу якість: " + quality + " (було: " + existingQuality + ")");
                            }
                        }
                        isDuplicate = true;
                        break;
                    }
                }

                if (!isDuplicate) {
                    interceptedFxPhotos.add(url);
                    if (verbose) {
                        System.out.println("📸 Перехоплено фото якості " + quality + ": " + cleanUrl.substring(cleanUrl.lastIndexOf("/") + 1));
                    }
                }
            }
        });
    }

    private boolean isBetterQuality(String newQuality, String existingQuality) {
        // Порядок якості від найкращої до найгіршої
        String[] qualityOrder = {"fx", "lg", "md", "sm", "xs", "thumb"};

        int newIndex = -1;
        int existingIndex = -1;

        for (int i = 0; i < qualityOrder.length; i++) {
            if (qualityOrder[i].equals(newQuality)) newIndex = i;
            if (qualityOrder[i].equals(existingQuality)) existingIndex = i;
        }

        // Менший індекс = краща якість
        return newIndex >= 0 && existingIndex >= 0 && newIndex < existingIndex;
    }

    private ChromeDriver setupDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/115.0.0.0 Safari/537.36");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-notifications");

        // options.addArguments("--headless=new"); // Вимкнено headless режим для візуалізації браузера

        // Оптимізація для швидкості
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-web-security");
        options.addArguments("--disable-features=VizDisplayCompositor");

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5)); // Оптимізовано для швидкості
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15)); // Таймаут завантаження сторінки
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(15)); // Таймаут виконання скриптів
        return driver;
    }

    private DevTools setupDevTools(ChromeDriver driver) {
        DevTools devTools = driver.getDevTools();
        devTools.createSession();

        devTools.send(Network.enable(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),   // Optional<Boolean>
                Optional.empty()    // Optional<Boolean>
        ));

        return devTools;
    }


    private String[] setupHashListener(DevTools devTools) {
        final String[] hashHolder = {null};
        devTools.addListener(Network.requestWillBeSent(), request -> {
            Request req = request.getRequest();
            String url = req.getUrl();
            if (url.contains("getOwnerAndAgencyData")) {
                Matcher matcher = Pattern.compile("/getOwnerAndAgencyData/(.*?)\\?").matcher(url);
                if (matcher.find()) {
                    hashHolder[0] = matcher.group(1);
                }
            }
        });
        return hashHolder;
    }

    private static class ParserStats {
        int shown = 0;
        int filteredEmptyDate = 0;
        int filteredTooOld = 0;
        int filteredNoUrl = 0;
        int totalFound = 0;
        int skippedAlreadyExists = 0; // Додано для відстеження пропущених через вже існуючі

        void printSummary(int hoursLimit) {
            System.out.println("\n✅ Завершено. Виведено квартир: " + shown);
            System.out.println("🔎 Всього оголошень на сторінках: " + totalFound);
            System.out.println("⏱ Відсіяно через дату (пусту): " + filteredEmptyDate);
            System.out.println("⏰ Відсіяно через дату (>" + hoursLimit + " год): " + filteredTooOld);
            System.out.println("🚫 Відсіяно через відсутність URL: " + filteredNoUrl);
            System.out.println("⏭️ Пропущено через вже існуючі: " + skippedAlreadyExists);
        }
    }
}
