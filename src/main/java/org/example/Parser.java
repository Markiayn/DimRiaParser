package org.example;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.json.*;

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

import static org.example.SQLiteJDBC.*;

public class Parser {

    public static void main(String[] args) throws Exception {
        parseRiaApartments(
                "Apartments_IvanoFrankivsk",
                "src/main/java/org/example/chromedriver-win64/chromedriver.exe",
                15,
                null,
                2,
                3,
                24,
                2,
                1,
                25.0,
                5,
                true
        );
    }

    public static void parseRiaApartments(
            String table_name,
            String chromeDriverPath,
            int regionId,
            Integer cityId,
            int realtyType,
            int operationType,
            int hoursLimit,
            int maxPages,
            int minRooms,
            double minArea,
            int maxPhotos,
            boolean verbose
    ) throws Exception {

        createTable();
        System.setProperty("webdriver.chrome.driver", chromeDriverPath);

        ChromeDriver driver = setupDriver();
        DevTools devTools = setupDevTools(driver);
        String[] hashHolder = setupHashListener(devTools);
        String[] phoneHolder = new String[1];

        ParserStats stats = new ParserStats();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int page = 0; page < maxPages; page++) {
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

            if (verbose) System.out.println("\n📄 Сторінка " + page + ":");

            Connection.Response response = Jsoup.connect(url.toString())
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute();

            JSONObject searchResult = new JSONObject(response.body());
            JSONArray items = searchResult.optJSONArray("items");
            if (items == null || items.isEmpty()) break;

            stats.totalFound += items.length();

            for (int i = 0; i < items.length(); i++) {
                int id = items.getInt(i);
                if (processApartmentWithFilters(table_name, id, driver, formatter, hashHolder, phoneHolder, hoursLimit, stats, minRooms, minArea, maxPhotos, verbose)) {
                    stats.shown++;
                }
            }
        }

        driver.quit();
        stats.printSummary(hoursLimit);
    }

    private static boolean processApartmentWithFilters(String table_name,int id, ChromeDriver driver, DateTimeFormatter formatter, String[] hashHolder,
                                                       String[] phoneHolder, int hoursLimit, ParserStats stats,
                                                       int minRooms, double minArea, int maxPhotos, boolean verbose) {
        try {
            JSONObject data = new JSONObject(Jsoup.connect("https://dom.ria.com/realty/data/" + id + "?lang_id=4&key=")
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute().body());

            String pubDateStr = data.optString("publishing_date");
            if (pubDateStr == null || pubDateStr.isEmpty()) {
                stats.filteredEmptyDate++;
                return false;
            }

            LocalDateTime published = LocalDateTime.parse(pubDateStr, formatter);
            if (Duration.between(published, LocalDateTime.now()).toHours() > hoursLimit) {
                stats.filteredTooOld++;
                return false;
            }

            int rooms = data.optInt("rooms_count");
            double area = data.optDouble("total_square_meters");
            if (rooms < minRooms || area < minArea) return false;

            String beautifulUrl = data.optString("beautiful_url");
            if (beautifulUrl.isEmpty()) {
                stats.filteredNoUrl++;
                return false;
            }

            String description = data.optString("description_uk");
            int price = data.optInt("price");
            int floor = data.optInt("floor");
            int floorsCount = data.optInt("floors_count");
            String street = data.optString("street_name_uk");
            String building = data.optString("building_number_str");
            String address = street + ", буд. " + building;

            List<String> photoPaths = new ArrayList<>();
            JSONObject photosObj = data.optJSONObject("photos");
            if (photosObj != null) {
                String slug = beautifulUrl.replaceAll("-\\d+\\.html$", "");
                int counter = 1;
                for (String key : photosObj.keySet()) {
                    JSONObject photoData = photosObj.getJSONObject(key);
                    String photoId = photoData.optString("id");
                    String filePath = photoData.optString("file");
                    String photoFileName = "photos/" + id + "_" + counter + ".jpg";
                    boolean downloaded = false;

                    Files.createDirectories(Paths.get("photos"));

                    if (photoId != null && !photoId.isEmpty()) {
                        String baseUrl = "https://cdn.riastatic.com/photosnew/dom/photo/" + slug + "__" + photoId;
                        String[] suffixes = {"b.jpg", "fl.webp", "fl.jpg"};
                        for (String suffix : suffixes) {
                            String photoUrl = baseUrl + suffix;
                            try (InputStream in = new URL(photoUrl).openStream()) {
                                Files.copy(in, Paths.get(photoFileName), StandardCopyOption.REPLACE_EXISTING);
                                downloaded = true;
                                break;
                            } catch (IOException e) {
                                if (verbose) System.out.println("⚠️ Не вдалося завантажити фото: " + photoUrl);
                            }
                        }
                    }

                    if (!downloaded && filePath != null && !filePath.isEmpty()) {
                        String fallbackUrl = "https://cdn.riastatic.com/photosnew/" + filePath;
                        try (InputStream in = new URL(fallbackUrl).openStream()) {
                            Files.copy(in, Paths.get(photoFileName), StandardCopyOption.REPLACE_EXISTING);
                            downloaded = true;
                        } catch (IOException e) {
                            if (verbose) System.out.println("⚠️ Не вдалося завантажити fallback-фото: " + fallbackUrl);
                        }
                    }

                    if (downloaded) {
                        photoPaths.add(photoFileName);
                        counter++;
                        if (photoPaths.size() == maxPhotos) break;
                    }
                }
            }

            driver.get("https://dom.ria.com/uk/" + beautifulUrl);
            Thread.sleep(2000);
            fetchAndPrintPhone(hashHolder, phoneHolder);

            insertApartment(table_name, id, description, address, price, phoneHolder[0], floor, floorsCount, rooms, area, photoPaths.toArray(new String[0]), pubDateStr);
            return true;

        } catch (Exception ex) {
            if (verbose) System.out.println("⛔ Помилка при обробці ID " + id + ": " + ex.getMessage());
            return false;
        }
    }

    private static ChromeDriver setupDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/115.0.0.0 Safari/537.36");
        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        return driver;
    }

    private static DevTools setupDevTools(ChromeDriver driver) {
        DevTools devTools = driver.getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
        return devTools;
    }

    private static String[] setupHashListener(DevTools devTools) {
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

    private static void fetchAndPrintPhone(String[] hashHolder, String[] phoneHolder) throws Exception {
        String hash = hashHolder[0];
        if (hash != null) {
            String apiUrl = "https://dom.ria.com/v1/api/realty/getOwnerAndAgencyData/" + hash + "?spa_final_page=true";
            JSONObject obj = new JSONObject(Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute().body());

            String phone = obj.getJSONObject("owner").getJSONArray("phones").getJSONObject(0).getString("phone_num");
            phoneHolder[0] = phone;
            System.out.println("📞 Номер телефону: " + phone);
        } else {
            System.out.println("❌ Hash не перехоплено.");
            phoneHolder[0] = null;
        }
        System.out.println("──────────────────────────────");
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
