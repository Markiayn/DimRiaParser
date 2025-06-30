package org.example.service;

import org.example.config.AppConfig;
import org.example.model.Apartment;
import org.json.JSONObject;
import org.json.JSONArray;
import org.jsoup.Jsoup;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class TelegramService {
    private final String botToken;
    private final String chatId1;
    private final String chatId2;
    private final boolean verbose;
    
    public TelegramService() {
        this.botToken = AppConfig.getTelegramBotToken();
        this.chatId1 = AppConfig.getTelegramChatId1();
        this.chatId2 = AppConfig.getTelegramChatId2();
        this.verbose = AppConfig.isVerbose();
    }
    
    /**
     * Відправляє різні квартири в різні канали
     */
    public boolean sendDifferentApartmentsToChannels(Apartment apartment1, Apartment apartment2) {
        boolean success1 = false;
        boolean success2 = false;
        
        if (apartment1 != null) {
            success1 = sendApartmentPost(apartment1, chatId1);
        }
        
        if (apartment2 != null) {
            success2 = sendApartmentPost(apartment2, chatId2);
        }
        
        return success1 || success2;
    }
    
    /**
     * Відправляє пост про квартиру в Telegram з фото в повідомленні
     */
    public boolean sendApartmentPost(Apartment apartment, String chatId) {
        try {
            String message = formatApartmentMessage(apartment);
            List<String> photos = apartment.getPhotoPaths();
            
            if (photos != null && !photos.isEmpty()) {
                // Відправляємо повідомлення з усіма фото
                boolean success = sendMessageWithAllPhotos(chatId, message, photos, apartment.getId());
                
                if (success) {
                    // Підраховуємо реальну кількість існуючих фото
                    int existingPhotos = 0;
                    for (String photoPath : photos) {
                        if (new File(photoPath).exists()) {
                            existingPhotos++;
                        }
                    }
                    
                    if (verbose) {
                        if (existingPhotos > 0) {
                            System.out.println("✅ Пост з " + existingPhotos + " фото відправлено в чат " + chatId + " для квартири " + apartment.getId());
                        } else {
                            System.out.println("✅ Пост без фото відправлено в чат " + chatId + " для квартири " + apartment.getId());
                        }
                    }
                }
                
                return success;
            } else {
                // Якщо фото немає, відправляємо тільки текст
                String textResponse = sendMessage(chatId, message);
                if (textResponse != null) {
                    if (verbose) {
                        System.out.println("✅ Пост без фото відправлено в чат " + chatId + " для квартири " + apartment.getId());
                    }
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("❌ Помилка відправки поста в Telegram: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Відправляє повідомлення в обидва канали (застарілий метод)
     */
    public boolean sendToBothChannels(Apartment apartment) {
        boolean success1 = sendApartmentPost(apartment, chatId1);
        boolean success2 = sendApartmentPost(apartment, chatId2);
        
        return success1 || success2;
    }
    
    /**
     * Форматує повідомлення про квартиру (без посилання та дати)
     */
    private String formatApartmentMessage(Apartment apartment) {
        StringBuilder message = new StringBuilder();
        
        message.append("🏠 *НОВА КВАРТИРА ДЛЯ ОРЕНДИ*\n\n");
        
        if (apartment.getDescription() != null && !apartment.getDescription().isEmpty()) {
            message.append("📝 *Опис:* ").append(apartment.getDescription()).append("\n\n");
        }
        
        message.append("📍 *Адреса:* ").append(apartment.getAddress()).append("\n");
        message.append("💰 *Ціна:* ").append(formatPrice(apartment.getPrice())).append("\n");
        message.append("🏢 *Поверх:* ").append(apartment.getFloor()).append("/").append(apartment.getFloorsCount()).append("\n");
        message.append("🛏 *Кімнат:* ").append(apartment.getRooms()).append("\n");
        message.append("📐 *Площа:* ").append(apartment.getArea()).append(" м²\n");
        
        if (apartment.getPhone() != null && !apartment.getPhone().isEmpty()) {
            message.append("📞 *Телефон:* `").append(apartment.getPhone()).append("`\n");
        }
        
        return message.toString();
    }
    
    /**
     * Форматує ціну
     */
    private String formatPrice(int price) {
        if (price >= 1000) {
            return String.format("%d грн/міс", price);
        } else {
            return String.format("%d грн", price);
        }
    }
    
    /**
     * Відправляє текстове повідомлення
     */
    private String sendMessage(String chatId, String text) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", text);
            requestBody.put("parse_mode", "Markdown");
            requestBody.put("disable_web_page_preview", true);
            
            String response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .requestBody(requestBody.toString())
                    .header("Content-Type", "application/json")
                    .post()
                    .body()
                    .text();
            
            JSONObject responseJson = new JSONObject(response);
            return responseJson.getBoolean("ok") ? response : null;
            
        } catch (Exception e) {
            System.err.println("❌ Помилка відправки повідомлення: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Відправляє повідомлення з фото
     */
    private boolean sendMessageWithPhoto(String chatId, String message, String photoPath, int apartmentId) {
        try {
            File photoFile = new File(photoPath);
            if (!photoFile.exists()) {
                if (verbose) {
                    System.out.println("⚠️ Файл фото не знайдено: " + photoPath);
                }
                return false;
            }
            
            String url = String.format("https://api.telegram.org/bot%s/sendPhoto", botToken);
            
            String response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .data("chat_id", chatId)
                    .data("caption", message)
                    .data("parse_mode", "Markdown")
                    .data("photo", photoFile.getName(), new java.io.FileInputStream(photoFile), "image/webp")
                    .post()
                    .body()
                    .text();
            
            JSONObject responseJson = new JSONObject(response);
            return responseJson.getBoolean("ok");
            
        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка відправки повідомлення з фото: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Перевіряє чи працює бот
     */
    public boolean testConnection() {
        try {
            String url = String.format("https://api.telegram.org/bot%s/getMe", botToken);
            String response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .get()
                    .body()
                    .text();
            
            JSONObject responseJson = new JSONObject(response);
            boolean isOk = responseJson.getBoolean("ok");
            
            if (isOk && verbose) {
                JSONObject result = responseJson.getJSONObject("result");
                System.out.println("✅ Telegram бот підключено: @" + result.getString("username"));
            }
            
            return isOk;
            
        } catch (Exception e) {
            System.err.println("❌ Помилка підключення до Telegram: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Відправляє повідомлення з усіма фото
     */
    private boolean sendMessageWithAllPhotos(String chatId, String message, List<String> photoPaths, int apartmentId) {
        try {
            // Перевіряємо наявність фото та фільтруємо тільки існуючі
            List<File> photoFiles = new ArrayList<>();
            for (String photoPath : photoPaths) {
                File photoFile = new File(photoPath);
                if (photoFile.exists()) {
                    photoFiles.add(photoFile);
                } else if (verbose) {
                    System.out.println("⚠️ Файл фото не знайдено: " + photoPath);
                }
            }
            
            if (photoFiles.isEmpty()) {
                if (verbose) {
                    System.out.println("⚠️ Немає валідних фото для квартири " + apartmentId + ", відправляємо тільки текст");
                }
                // Якщо фото немає, відправляємо тільки текст
                String textResponse = sendMessage(chatId, message);
                return textResponse != null;
            }
            
            // Якщо тільки одне фото, використовуємо sendPhoto
            if (photoFiles.size() == 1) {
                return sendMessageWithPhoto(chatId, message, photoFiles.get(0).getAbsolutePath(), apartmentId);
            }
            
            // Якщо кілька фото, використовуємо sendMediaGroup
            return sendMediaGroup(chatId, message, photoFiles, apartmentId);
            
        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка відправки повідомлення з усіма фото: " + e.getMessage());
            }
            return false;
        }
    }
    
    /**
     * Відправляє групу медіа (кілька фото з підписом)
     */
    private boolean sendMediaGroup(String chatId, String message, List<File> photoFiles, int apartmentId) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMediaGroup", botToken);
            
            // Створюємо JSON для sendMediaGroup
            JSONArray mediaArray = new JSONArray();
            
            for (int i = 0; i < photoFiles.size(); i++) {
                File photoFile = photoFiles.get(i);
                JSONObject mediaItem = new JSONObject();
                mediaItem.put("type", "photo");
                mediaItem.put("media", "attach://photo" + i);
                
                // Додаємо підпис тільки до першого фото
                if (i == 0) {
                    mediaItem.put("caption", message);
                    mediaItem.put("parse_mode", "Markdown");
                }
                
                mediaArray.put(mediaItem);
            }
            
            // Створюємо multipart запит
            org.jsoup.Connection connection = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .method(org.jsoup.Connection.Method.POST);
            
            // Додаємо chat_id та media
            connection.data("chat_id", chatId);
            connection.data("media", mediaArray.toString());
            
            // Додаємо всі файли
            for (int i = 0; i < photoFiles.size(); i++) {
                File photoFile = photoFiles.get(i);
                connection.data("photo" + i, photoFile.getName(), new java.io.FileInputStream(photoFile), "image/webp");
            }
            
            String response = connection.execute().body();
            
            JSONObject responseJson = new JSONObject(response);
            boolean success = responseJson.getBoolean("ok");
            
            if (!success && verbose) {
                System.err.println("❌ Telegram API помилка: " + responseJson.optString("description", "Невідома помилка"));
            }
            
            return success;
            
        } catch (Exception e) {
            if (verbose) {
                System.err.println("❌ Помилка відправки групи медіа: " + e.getMessage());
            }
            return false;
        }
    }
} 
