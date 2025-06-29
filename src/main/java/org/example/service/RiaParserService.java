package org.example.service;

import org.example.config.AppConfig;
import org.example.database.DatabaseManager;
import org.example.model.Apartment;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.json.*;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v136.network.Network;
import org.openqa.selenium.devtools.v136.network.model.Request;

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
    
    public RiaParserService() {
        this.databaseManager = DatabaseManager.getInstance();
        this.photosDirectory = AppConfig.getPhotosDirectory();
        this.verbose = AppConfig.isVerbose();
    }
    
    public void parseApartments(String tableName, int regionId, Integer cityId, 
                               int realtyType, int operationType, int hoursLimit, 
                               int maxPages, int minRooms, double minArea, int maxPhotos) {
        
        // Створюємо таблицю якщо не існує
        databaseManager.createTable(tableName);
        
        // Налаштовуємо Chrome Driver
        System.setProperty("webdriver.chrome.driver", AppConfig.getChromeDriverPath());
        
        ChromeDriver driver = null;
        try {
            driver = setupDriver();
            DevTools devTools = setupDevTools(driver);
            String[] hashHolder = setupHashListener(devTools);
            String[] phoneHolder = new String[1];
            
            // Налаштовуємо перехоплення фотографій
            setupPhotoInterceptor(devTools);
            
            ParserStats stats = new ParserStats();
            
            // Парсимо сторінки
            for (int page = 0; page < maxPages; page++) {
                if (!parsePage(tableName, page, regionId, cityId, realtyType, operationType, 
                             hoursLimit, minRooms, minArea, maxPhotos, driver, formatter, 
                             hashHolder, phoneHolder, stats)) {
                    break; // Якщо немає більше даних, виходимо
                }
            }
            
            stats.printSummary(hoursLimit);
            
        } catch (Exception e) {
            System.err.println("❌ Критична помилка при парсингу: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
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
                System.out.println("\n📄 Сторінка " + page + ": " + url);
            }
            
            Connection.Response response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute();
            
            JSONObject searchResult = new JSONObject(response.body());
            JSONArray items = searchResult.optJSONArray("items");
            
            if (items == null || items.isEmpty()) {
                if (verbose) System.out.println("📄 Більше оголошень немає");
                return false;
            }
            
            stats.totalFound += items.length();
            
            for (int i = 0; i < items.length(); i++) {
                int id = items.getInt(i);
                if (processApartment(tableName, id, driver, formatter, hashHolder, 
                                   phoneHolder, hoursLimit, stats, minRooms, minArea, maxPhotos)) {
                    stats.shown++;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("❌ Помилка при парсингу сторінки " + page + ": " + e.getMessage());
            return false;
        }
    }
    
    private String buildSearchUrl(int page, int regionId, Integer cityId, int realtyType, int operationType) {
        StringBuilder url = new StringBuilder("https://dom.ria.com/node/searchEngine/v2/?")
                .append("addMoreRealty=false&excludeSold=1&category=1")
                .append("&realty_type=").append(realtyType)
                .append("&operation=").append(operationType)
                .append("&state_id=").append(regionId)
                .append("&price_cur=1&wo_dupl=1&sort=created_at")
                .append("&firstIteraction=false&limit=20&type=list&client=searchV2");
        
        if (cityId != null) {
            url.append("&city_id=").append(cityId);
        }
        
        url.append("&page=").append(page);
        return url.toString();
    }
    
    private boolean processApartment(String tableName, int id, ChromeDriver driver, 
                                   DateTimeFormatter formatter, String[] hashHolder,
                                   String[] phoneHolder, int hoursLimit, ParserStats stats,
                                   int minRooms, double minArea, int maxPhotos) {
        try {
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
                    .execute().body();
            return new JSONObject(response);
        } catch (Exception e) {
            System.err.println("❌ Помилка отримання даних для ID " + id + ": " + e.getMessage());
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
            
            // Натискаємо "Дивитися всі фото"
            try {
                WebElement showAllPhotosButton = driver.findElement(By.cssSelector("li[class*='photo-'] span.all-photos"));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", showAllPhotosButton);
                if (verbose) System.out.println("🖼 Натиснуто 'Дивитися всі фото'");
            } catch (Exception e) {
                if (verbose) System.out.println("⚠️ Кнопка 'Дивитися всі фото' не знайдена");
            }
            
            // Прокручуємо фотографії
            for (int i = 0; i < 5; i++) {
                try {
                    WebElement nextButton = driver.findElement(By.cssSelector("button.rotate-btn.rotate-arr-r"));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextButton);
                    Thread.sleep(500);
                } catch (Exception e) {
                    break;
                }
            }
            
            Thread.sleep(1500);
            
            // Завантажуємо фотографії
            int counter = 1;
            for (String photoUrl : interceptedFxPhotos) {
                String photoFileName = photosDirectory + "/" + apartment.getId() + "_net_" + counter + ".webp";
                
                try (InputStream in = new URL(photoUrl).openStream()) {
                    Files.createDirectories(Paths.get(photosDirectory));
                    Files.copy(in, Paths.get(photoFileName), StandardCopyOption.REPLACE_EXISTING);
                    apartment.addPhotoPath(photoFileName);
                    counter++;
                    if (apartment.getPhotoPaths().size() >= maxPhotos) break;
                } catch (IOException ignored) {}
            }
            interceptedFxPhotos.clear();
            
        } catch (Exception e) {
            System.err.println("❌ Помилка завантаження фотографій: " + e.getMessage());
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
                        .execute().body());
                
                String phone = obj.getJSONObject("owner").getJSONArray("phones").getJSONObject(0).getString("phone_num");
                phoneHolder[0] = phone;
                if (verbose) System.out.println("📞 Номер телефону: " + phone);
                return phone;
            } else {
                if (verbose) System.out.println("❌ Hash не перехоплено.");
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Помилка отримання телефону: " + e.getMessage());
            return null;
        }
    }
    
    private void setupPhotoInterceptor(DevTools devTools) {
        devTools.addListener(Network.requestWillBeSent(), request -> {
            String url = request.getRequest().getUrl();
            if (url.endsWith("fx.webp") && url.contains("photosnew/dom/photo/")) {
                interceptedFxPhotos.add(url);
            }
        });
    }
    
    private ChromeDriver setupDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/115.0.0.0 Safari/537.36");
        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        return driver;
    }
    
    private DevTools setupDevTools(ChromeDriver driver) {
        DevTools devTools = driver.getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
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
        
        void printSummary(int hoursLimit) {
            System.out.println("\n✅ Завершено. Виведено квартир: " + shown);
            System.out.println("🔎 Всього оголошень на сторінках: " + totalFound);
            System.out.println("⏱ Відсіяно через дату (пусту): " + filteredEmptyDate);
            System.out.println("⏰ Відсіяно через дату (>" + hoursLimit + " год): " + filteredTooOld);
            System.out.println("🚫 Відсіяно через відсутність URL: " + filteredNoUrl);
        }
    }
} 