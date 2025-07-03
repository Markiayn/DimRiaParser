package org.example.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class CityConfig {
    public static class City {
        public final String name;
        public final int cityId;
        public final String dbTable;
        public final String channel1;
        public final String channel2;
        public final int hours;

        public City(String name, int cityId, String dbTable, String channel1, String channel2, int hours) {
            this.name = name;
            this.cityId = cityId;
            this.dbTable = dbTable;
            this.channel1 = channel1;
            this.channel2 = channel2;
            this.hours = hours;
        }
    }

    private static final List<City> cities = new ArrayList<>();
    private static boolean loaded = false;

    public static List<City> getCities() {
        if (!loaded) loadCities();
        return cities;
    }

    private static void loadCities() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
        } catch (IOException e) {
            System.err.println("[CityConfig] Не вдалося завантажити config.properties: " + e.getMessage());
            return;
        }
        int i = 1;
        while (true) {
            String prefix = "city." + i + ".";
            String name = props.getProperty(prefix + "name");
            String idStr = props.getProperty(prefix + "id");
            String db = props.getProperty(prefix + "db");
            String ch1 = props.getProperty(prefix + "channel1", "");
            String ch2 = props.getProperty(prefix + "channel2", "");
            String hoursStr = props.getProperty(prefix + "hours", "24");
            if (name == null || idStr == null || db == null) break;
            int cityId = Integer.parseInt(idStr);
            int hours = Integer.parseInt(hoursStr);
            cities.add(new City(name, cityId, db, ch1, ch2, hours));
            i++;
        }
        loaded = true;
    }
} 