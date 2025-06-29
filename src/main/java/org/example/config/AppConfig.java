package org.example.config;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class AppConfig {
    private static final Properties properties = new Properties();
    
    static {
        try {
            properties.load(new FileInputStream("config.properties"));
        } catch (IOException e) {
            // Використовуємо значення за замовчуванням
            setDefaultProperties();
        }
    }
    
    private static void setDefaultProperties() {
        properties.setProperty("chrome.driver.path", "src/main/java/org/example/chromedriver-win64/chromedriver.exe");
        properties.setProperty("database.url", "jdbc:sqlite:test.db");
        properties.setProperty("photos.directory", "photos");
        properties.setProperty("max.photos.per.apartment", "5");
        properties.setProperty("hours.limit", "48");
        properties.setProperty("max.pages", "2");
        properties.setProperty("min.rooms", "1");
        properties.setProperty("min.area", "25.0");
        properties.setProperty("verbose", "true");
    }
    
    public static String getChromeDriverPath() {
        return properties.getProperty("chrome.driver.path");
    }
    
    public static String getDatabaseUrl() {
        return properties.getProperty("database.url");
    }
    
    public static String getPhotosDirectory() {
        return properties.getProperty("photos.directory");
    }
    
    public static int getMaxPhotosPerApartment() {
        return Integer.parseInt(properties.getProperty("max.photos.per.apartment"));
    }
    
    public static int getHoursLimit() {
        return Integer.parseInt(properties.getProperty("hours.limit"));
    }
    
    public static int getMaxPages() {
        return Integer.parseInt(properties.getProperty("max.pages"));
    }
    
    public static int getMinRooms() {
        return Integer.parseInt(properties.getProperty("min.rooms"));
    }
    
    public static double getMinArea() {
        return Double.parseDouble(properties.getProperty("min.area"));
    }
    
    public static boolean isVerbose() {
        return Boolean.parseBoolean(properties.getProperty("verbose"));
    }
} 