package org.example;

import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.json.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RiaApartmentIdFetcher {

    public static void main(String[] args) throws Exception {
        final String baseUrl = "https://dom.ria.com/node/searchEngine/v2/";
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        int page = 0;
        int found = 0;
        List<Integer> freshIds = new ArrayList<>();

        while (true) {
            String searchUrl = baseUrl +
                    "?addMoreRealty=false&excludeSold=1&category=1&realty_type=2&operation=3" +
                    "&state_id=15&city_id=0&in_radius=0&with_newbuilds=0&price_cur=1&wo_dupl=1" +
                    "&complex_inspected=0&sort=inspected_sort&period=0&notFirstFloor=0&notLastFloor=0" +
                    "&with_map=0&photos_count_from=0&firstIteraction=false&fromAmp=0" +
                    "&city_ids=15&limit=20&type=list&client=searchV2&operation_type=3&page=" + page;

            Connection.Response response = Jsoup.connect(searchUrl)
                    .ignoreContentType(true)
                    .userAgent("Mozilla/5.0")
                    .execute();

            JSONObject json = new JSONObject(response.body());
            JSONArray items = json.getJSONArray("items");

            if (items.isEmpty()) break;

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

                    LocalDateTime pubDate = LocalDateTime.parse(pubDateStr, formatter);
                    long hoursAgo = Duration.between(pubDate, LocalDateTime.now()).toHours();

                    if (hoursAgo <= 10) {
                        freshIds.add(id);
                        System.out.println(id);
                        found++;
                    }

                } catch (Exception e) {
                    System.out.println("⛔ Помилка ID " + id + ": " + e.getMessage());
                }
            }

            page++;
        }

        System.out.println("✅ Знайдено квартир: " + found);
    }
}
