package org.example.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.Set;
import java.util.HashSet;

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
            System.out.println("[CityConfig] config.properties завантажено успішно");
        } catch (IOException e) {
            System.err.println("[CityConfig] Не вдалося завантажити config.properties: " + e.getMessage());
            return;
        }
        
        // Додаємо логування всіх властивостей для діагностики
        System.out.println("[CityConfig] Діагностика - всі властивості з config.properties:");
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("city.")) {
                System.out.println("  " + key + " = " + props.getProperty(key));
            }
        }
        
        // Знаходимо всі унікальні номери міст
        Set<Integer> cityNumbers = new HashSet<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("city.") && key.endsWith(".name")) {
                try {
                    String numberStr = key.substring(5, key.lastIndexOf("."));
                    int cityNumber = Integer.parseInt(numberStr);
                    cityNumbers.add(cityNumber);
                } catch (NumberFormatException e) {
                    System.err.println("[CityConfig] Помилка парсингу номера міста з ключа: " + key);
                }
            }
        }
        
        System.out.println("[CityConfig] Знайдено номери міст: " + cityNumbers);
        
        // Завантажуємо міста за знайденими номерами
        for (int cityNumber : cityNumbers) {
            String prefix = "city." + cityNumber + ".";
            String name = props.getProperty(prefix + "name");
            String idStr = props.getProperty(prefix + "id");
            String db = props.getProperty(prefix + "db");
            String ch1 = props.getProperty(prefix + "channel1", "");
            String ch2 = props.getProperty(prefix + "channel2", "");
            String hoursStr = props.getProperty(prefix + "hours", "24");
            
            System.out.println("[CityConfig] Перевірка міста " + cityNumber + ":");
            System.out.println("  name = " + (name != null ? "'" + name + "'" : "null"));
            System.out.println("  id = " + (idStr != null ? "'" + idStr + "'" : "null"));
            System.out.println("  db = " + (db != null ? "'" + db + "'" : "null"));
            System.out.println("  channel1 = " + (ch1 != null ? "'" + ch1 + "'" : "null"));
            System.out.println("  channel2 = " + (ch2 != null ? "'" + ch2 + "'" : "null"));
            
            if (name == null || idStr == null || db == null) {
                System.out.println("[CityConfig] Пропущено місто " + cityNumber + " (не знайдено обов'язкових полів)");
                continue;
            }
            
            try {
                int cityId = Integer.parseInt(idStr);
                int hours = Integer.parseInt(hoursStr);
                cities.add(new City(name, cityId, db, ch1, ch2, hours));
                System.out.println("[CityConfig] Додано місто: " + name + " (ID: " + cityId + ", БД: " + db + ", Канал1: " + ch1 + ", Канал2: " + ch2 + ")");
            } catch (NumberFormatException e) {
                System.err.println("[CityConfig] Помилка парсингу числа для міста " + cityNumber + ": " + e.getMessage());
            }
        }
        
        System.out.println("[CityConfig] Завантажено " + cities.size() + " міст");
        loaded = true;
    }
} 