package org.example;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.json.*;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v136.network.Network;
import org.openqa.selenium.devtools.v136.network.model.Request;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class RiaApartmentParser {

    public static void main(String[] args) throws Exception {
        // ░░░░░░░░░░ НАЛАШТУВАННЯ ░░░░░░░░░░
        String chromedriverPath = "src/main/java/org/example/chromedriver-win64/chromedriver.exe";
        int hoursLimit = 35; // максимум годин від моменту публікації
        int maxPages = 10;    // максимальна кількість сторінок (можеш збільшити)
        int shown = 0;        // лічильник успішно виведених квартир
        // ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░

        System.setProperty("webdriver.chrome.driver", chromedriverPath);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/115.0.0.0 Safari/537.36");

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));

        DevTools devTools = driver.getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));

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

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (int page = 0; page < maxPages; page++) {
            String url = "https://dom.ria.com/node/searchEngine/v2/?" +
                    "addMoreRealty=false&excludeSold=1&category=1&realty_type=2&operation=3" +
                    "&state_id=5&price_cur=1&wo_dupl=1&sort=inspected_sort" +
                    "&firstIteraction=false&limit=20&market=3&type=list&client=searchV2&ch=246_244" +
                    "&page=" + page;

            System.out.println("\n📄 Сторінка " + page + ":");

            Connection.Response response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute();

            JSONObject searchResult = new JSONObject(response.body());
            JSONArray items = searchResult.optJSONArray("items");

            if (items == null || items.isEmpty()) {
                System.out.println("🔚 Сторінка пуста. Завершуємо.");
                break;
            }

            for (int i = 0; i < items.length(); i++) {
                int id = items.getInt(i);
                String detailUrl = "https://dom.ria.com/realty/data/" + id + "?lang_id=4&key=";

                try {
                    JSONObject data = new JSONObject(Jsoup.connect(detailUrl)
                            .ignoreContentType(true)
                            .userAgent("Mozilla/5.0")
                            .execute().body());

                    String pubDateStr = data.optString("publishing_date");
                    if (pubDateStr == null || pubDateStr.isEmpty()) continue;

                    LocalDateTime published = LocalDateTime.parse(pubDateStr, formatter);
                    if (Duration.between(published, LocalDateTime.now()).toHours() > hoursLimit) continue;

                    String beautifulUrl = data.optString("beautiful_url");
                    if (beautifulUrl.isEmpty()) continue;

                    hashHolder[0] = null;
                    String listingUrl = "https://dom.ria.com/uk/" + beautifulUrl;
                    driver.get(listingUrl);
                    Thread.sleep(2000);

                    // Деталі
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

                    System.out.println("\n📑 Оголошення:");
                    System.out.printf("Оренда %d-кімнатної квартири по вул. %s (%s)\n", rooms, address, district);
                    System.out.printf("\uD83D\uDCB5 %d грн + комунальні послуги\n", price);
                    System.out.printf("\uD83C\uDFE2 %d поверх з %d\n", floor, floorsCount);
                    System.out.println("\uD83C\uDFE1 " + description);

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

                    shown++;

                } catch (Exception ex) {
                    System.out.println("⛔ Помилка при обробці ID " + id + ": " + ex.getMessage());
                }

                System.out.println("──────────────────────────────");
            }
        }

        driver.quit();
        System.out.println("\n✅ Завершено. Виведено квартир: " + shown);
    }
}
