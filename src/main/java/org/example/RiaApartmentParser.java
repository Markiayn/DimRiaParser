// File: RiaApartmentParser.java
package org.example;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
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

public class RiaApartmentParser {

    public static void main(String[] args) throws Exception {
        String chromedriverPath = "src/main/java/org/example/chromedriver-win64/chromedriver.exe";
        int hoursLimit = 24;
        int maxPages = 2;

        System.setProperty("webdriver.chrome.driver", chromedriverPath);

        ChromeDriver driver = setupDriver();
        DevTools devTools = setupDevTools(driver);
        String[] hashHolder = setupHashListener(devTools);

        ParserStats stats = new ParserStats();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int page = 0; page < maxPages; page++) {
            JSONArray items = fetchPageItems(page);
            if (items == null || items.isEmpty()) break;

            stats.totalFound += items.length();

            for (int i = 0; i < items.length(); i++) {
                int id = items.getInt(i);
                if (processApartment(id, driver, formatter, hashHolder, hoursLimit, stats)) {
                    stats.shown++;
                }
            }
        }

        driver.quit();
        stats.printSummary(hoursLimit);
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

    private static JSONArray fetchPageItems(int page) throws Exception {
        String url = "https://dom.ria.com/node/searchEngine/v2/?" +
                "addMoreRealty=false&excludeSold=1&category=1&realty_type=2&operation=3" +
                "&state_id=5&price_cur=1&wo_dupl=1&sort=created_at" +
                "&firstIteraction=false&limit=20&type=list&client=searchV2&ch=246_244" +
                "&page=" + page;

        System.out.println("\n\uD83D\uDCC4 Сторінка " + page + ":");

        Connection.Response response = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent("Mozilla/5.0")
                .execute();

        JSONObject searchResult = new JSONObject(response.body());
        return searchResult.optJSONArray("items");
    }

    private static boolean processApartment(int id, ChromeDriver driver, DateTimeFormatter formatter, String[] hashHolder,
                                            int hoursLimit, ParserStats stats) {
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

            String beautifulUrl = data.optString("beautiful_url");
            if (beautifulUrl.isEmpty()) {
                stats.filteredNoUrl++;
                return false;
            }

            hashHolder[0] = null;
            driver.get("https://dom.ria.com/uk/" + beautifulUrl);
            Thread.sleep(2000);

            printApartmentDetails(data);
            fetchAndPrintPhone(hashHolder);
            downloadPhotos(data, id, beautifulUrl);

            return true;
        } catch (Exception ex) {
            System.out.println("⛔ Помилка при обробці ID " + id + ": " + ex.getMessage());
            return false;
        }
    }

    private static void printApartmentDetails(JSONObject data) {
        String description = data.optString("description_uk");
        int price = data.optInt("price");
        int floor = data.optInt("floor");
        int floorsCount = data.optInt("floors_count");
        String district = data.optString("district_name_uk");
        String street = data.optString("street_name_uk");
        String building = data.optString("building_number_str");
        int rooms = data.optInt("rooms_count");
        double area = data.optDouble("total_square_meters");
        String address = street + ", буд. " + building;

        System.out.println("\n\uD83D\uDCC1 Оголошення:");
        System.out.printf("Оренда %d-кімнатної квартири по вул. %s (%s)\n", rooms, address, district);
        System.out.printf("\uD83D\uDCB5 %d грн + комунальні послуги\n", price);
        System.out.printf("\uD83C\uDFE2 %d поверх з %d\n", floor, floorsCount);
        System.out.println("\uD83C\uDFE1 " + description);
    }

    private static void fetchAndPrintPhone(String[] hashHolder) throws Exception {
        String hash = hashHolder[0];
        if (hash != null) {
            String apiUrl = "https://dom.ria.com/v1/api/realty/getOwnerAndAgencyData/" + hash + "?spa_final_page=true";
            JSONObject obj = new JSONObject(Jsoup.connect(apiUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute().body());

            String phone = obj.getJSONObject("owner").getJSONArray("phones").getJSONObject(0).getString("phone_num");
            System.out.println("\uD83D\uDCDE Номер телефону: " + phone);
        } else {
            System.out.println("❌ Hash не перехоплено.");
        }
        System.out.println("──────────────────────────────");
    }

    private static void downloadPhotos(JSONObject data, int id, String beautifulUrl) {
        JSONObject photosObj = data.optJSONObject("photos");
        if (photosObj == null || photosObj.isEmpty()) return;

        String slug = beautifulUrl.replaceAll("-\\d+\\.html$", "");
        int counter = 1;

        for (String key : photosObj.keySet()) {
            JSONObject photoData = photosObj.getJSONObject(key);
            String photoId = photoData.optString("id");
            if (photoId == null || photoId.isEmpty()) continue;

            String fullUrl = "https://cdn.riastatic.com/photosnew/dom/photo/" + slug + "__" + photoId + "b.jpg";

            try (InputStream in = new URL(fullUrl).openStream()) {
                Files.createDirectories(Paths.get("photos"));
                Files.copy(in, Paths.get("photos/" + id + "_" + counter + ".jpg"), StandardCopyOption.REPLACE_EXISTING);
                counter++;
            } catch (IOException e) {
                System.out.println("❌ Помилка при завантаженні фото " + fullUrl);
            }
        }
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