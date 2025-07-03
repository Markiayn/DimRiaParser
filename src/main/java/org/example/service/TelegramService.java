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
     * Відправляє різні квартири в різні кастомні канали
     */
    public boolean sendDifferentApartmentsToChannelsCustomChannels(Apartment apartment1, String channel1, Apartment apartment2, String channel2) {
        boolean success1 = false;
        boolean success2 = false;
        if (apartment1 != null && channel1 != null && !channel1.isEmpty()) {
            success1 = sendApartmentPost(apartment1, channel1);
        }
        if (apartment2 != null && channel2 != null && !channel2.isEmpty()) {
            success2 = sendApartmentPost(apartment2, channel2);
        }
        return success1 || success2;
    }
    
    /**
     * Відправляє пост про квартиру в Telegram (plain text, без Markdown)
     */
    public boolean sendApartmentPost(Apartment apartment, String chatId) {
        try {
            String message = formatApartmentMessagePlain(apartment);
            java.util.List<String> photos = apartment.getPhotoPaths();

            if (photos == null || photos.isEmpty()) {
                return false;
            }
            if (chatId == null || chatId.isEmpty()) {
                return false;
            }
            
            // Перевіряємо довжину повідомлення і логуємо якщо воно обрізане
            if (message.length() > 1024) {
                logWarn("[TELEGRAM] Повідомлення для квартири " + apartment.getId() + " було обрізано з " + message.length() + " до 1024 символів");
            }
            
            if (photos.size() == 1) {
                return sendMessageWithPhoto(chatId, message, photos.get(0), apartment.getId());
            } else if (photos.size() > 1) {
                return sendMediaGroup(chatId, message, photos, apartment.getId());
            }
            return false;
        } catch (Exception e) {
            logWarn("[TELEGRAM] Помилка відправки квартири " + apartment.getId() + ": " + e.getMessage());
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
     * Обрізає caption до 1024 символів, залишаючи хвіст (адреса, ціна, поверх, кімнати, площа, телефон)
     */
    private String trimCaption(String description, String tail) {
        final int MAX_LENGTH = 1024;
        if ((description + tail).length() <= MAX_LENGTH) {
            return description + tail;
        }
        int tailLen = tail.length();
        int allowedDescLen = MAX_LENGTH - tailLen;
        if (allowedDescLen <= 0) {
            // Якщо хвіст сам по собі довший за ліміт, обрізаємо його
            return tail.substring(tail.length() - MAX_LENGTH);
        }
        // Обрізаємо опис по останньому повному реченню
        String desc = description.substring(0, Math.min(description.length(), allowedDescLen));
        int lastDot = desc.lastIndexOf(".");
        int lastExcl = desc.lastIndexOf("!");
        int lastQuest = desc.lastIndexOf("?");
        int lastSentence = Math.max(lastDot, Math.max(lastExcl, lastQuest));
        if (lastSentence > 30) {
            desc = desc.substring(0, lastSentence + 1);
        }
        return desc.trim() + "\n\n" + tail.trim();
    }
    
    /**
     * Форматує повідомлення про квартиру (без посилання та дати)
     */
    private String formatApartmentMessage(Apartment apartment) {
        StringBuilder tail = new StringBuilder();
        tail.append("📍 *Адреса:* ").append(apartment.getAddress()).append("\n");
        tail.append("💰 *Ціна:* ").append(formatPrice(apartment.getPrice())).append("\n");
        tail.append("🏢 *Поверх:* ").append(apartment.getFloor()).append("/").append(apartment.getFloorsCount()).append("\n");
        tail.append("🛏 *Кімнат:* ").append(apartment.getRooms()).append("\n");
        tail.append("📐 *Площа:* ").append(apartment.getArea()).append(" м²\n");
        if (apartment.getPhone() != null && !apartment.getPhone().isEmpty()) {
            tail.append("📞 *Телефон:* `").append(apartment.getPhone()).append("`\n");
        }
        StringBuilder description = new StringBuilder();
        description.append("🏠 *НОВА КВАРТИРА ДЛЯ ОРЕНДИ*\n\n");
        if (apartment.getDescription() != null && !apartment.getDescription().isEmpty()) {
            description.append("📝 *Опис:* ").append(apartment.getDescription()).append("\n\n");
        }
        return trimCaption(description.toString(), tail.toString());
    }
    
    /**
     * Форматує ціну
     */
    private String formatPrice(int price) {
        if (price < 2000) {
            return String.format("%d $", price);
        } else {
            return String.format("%d грн/міс", price);
        }
    }
    
    /**
     * Відправляє текстове повідомлення
     */
    private String sendMessage(String chatId, String text) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            org.json.JSONObject requestBody = new org.json.JSONObject();
            requestBody.put("chat_id", chatId);
            requestBody.put("text", text);
            requestBody.put("disable_web_page_preview", true);
            String response = org.jsoup.Jsoup.connect(url)
                    .ignoreContentType(true)
                    .requestBody(requestBody.toString())
                    .header("Content-Type", "application/json")
                    .post()
                    .body()
                    .text();
            org.json.JSONObject responseJson = new org.json.JSONObject(response);
            return responseJson.getBoolean("ok") ? response : null;
        } catch (Exception e) {
            logWarn("[MESSAGE] Помилка відправки повідомлення: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Відправляє повідомлення з фото
     */
    private boolean sendMessageWithPhoto(String chatId, String message, String photoPath, int apartmentId) {
        try {
            java.io.File photoFile = new java.io.File(photoPath);
            if (!photoFile.exists()) {
                return false;
            }
            
            String url = String.format("https://api.telegram.org/bot%s/sendPhoto", botToken);
            String boundary = "*****" + System.currentTimeMillis() + "*****";
            
            java.net.URL urlObj = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            try (java.io.OutputStream outputStream = connection.getOutputStream();
                 java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"), true)) {
                
                // Додаємо chat_id
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n");
                writer.append("\r\n");
                writer.append(chatId).append("\r\n");
                
                // Додаємо caption
                if (message != null && !message.isEmpty()) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"caption\"").append("\r\n");
                    writer.append("\r\n");
                    writer.append(message).append("\r\n");
                }
                
                // Додаємо фото
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"").append(photoFile.getName()).append("\"").append("\r\n");
                writer.append("Content-Type: image/webp").append("\r\n");
                writer.append("\r\n");
                writer.flush();
                
                try (java.io.FileInputStream inputStream = new java.io.FileInputStream(photoFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                }
                
                writer.append("\r\n");
                writer.append("--").append(boundary).append("--").append("\r\n");
                writer.flush();
            }
            
            int responseCode = connection.getResponseCode();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            if (responseCode == 200) {
                org.json.JSONObject responseJson = new org.json.JSONObject(response.toString());
                return responseJson.optBoolean("ok", false);
            } else {
                // Логуємо помилку відправки
                String errorResponse = response.toString();
                logWarn("[TELEGRAM] Помилка відправки фото для квартири " + apartmentId + " (код " + responseCode + "): " + errorResponse);
                return false;
            }
            
        } catch (Exception e) {
            logWarn("[TELEGRAM] Виняток при відправці фото для квартири " + apartmentId + ": " + e.getMessage());
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
            return sendMediaGroup(chatId, message, photoPaths, apartmentId);
            
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
    private boolean sendMediaGroup(String chatId, String message, java.util.List<String> photoPaths, int apartmentId) {
        try {
            java.util.List<java.io.File> photoFiles = new java.util.ArrayList<>();
            for (String path : photoPaths) {
                java.io.File f = new java.io.File(path);
                if (f.exists()) {
                    photoFiles.add(f);
                }
            }
            
            if (photoFiles.size() < 2) {
                return false;
            }
            
            String url = String.format("https://api.telegram.org/bot%s/sendMediaGroup", botToken);
            String boundary = "*****" + System.currentTimeMillis() + "*****";
            
            // Створюємо JSON масив media
            org.json.JSONArray mediaArray = new org.json.JSONArray();
            for (int i = 0; i < photoFiles.size(); i++) {
                org.json.JSONObject mediaItem = new org.json.JSONObject();
                mediaItem.put("type", "photo");
                mediaItem.put("media", "attach://photo" + i);
                if (i == 0 && message != null && !message.isEmpty()) {
                    mediaItem.put("caption", message);
                }
                mediaArray.put(mediaItem);
            }
            
            java.net.URL urlObj = new java.net.URL(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            
            try (java.io.OutputStream outputStream = connection.getOutputStream();
                 java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"), true)) {
                
                // Додаємо chat_id
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n");
                writer.append("\r\n");
                writer.append(chatId).append("\r\n");
                
                // Додаємо media JSON
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"media\"").append("\r\n");
                writer.append("\r\n");
                writer.append(mediaArray.toString()).append("\r\n");
                
                // Додаємо всі фото
                for (int i = 0; i < photoFiles.size(); i++) {
                    java.io.File photoFile = photoFiles.get(i);
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"photo").append(String.valueOf(i)).append("\"; filename=\"").append(photoFile.getName()).append("\"").append("\r\n");
                    writer.append("Content-Type: image/webp").append("\r\n");
                    writer.append("\r\n");
                    writer.flush();
                    
                    try (java.io.FileInputStream inputStream = new java.io.FileInputStream(photoFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();
                    }
                    writer.append("\r\n");
                }
                
                writer.append("--").append(boundary).append("--").append("\r\n");
                writer.flush();
            }
            
            int responseCode = connection.getResponseCode();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            if (responseCode == 200) {
                org.json.JSONObject responseJson = new org.json.JSONObject(response.toString());
                return responseJson.optBoolean("ok", false);
            } else {
                return false;
            }
            
        } catch (Exception e) {
            return false;
        }
    }

    private String formatApartmentMessagePlain(Apartment apartment) {
        StringBuilder sb = new StringBuilder();
        sb.append("НОВА КВАРТИРА ДЛЯ ОРЕНДИ\n\n");
        
        // Важлива інформація, яка завжди повинна залишатися
        String importantInfo = String.format(
            "📍 Адреса: %s\n💰 Ціна: %s\n🏢 Поверх: %d/%d\n🛏 Кімнат: %d\n📐 Площа: %.1f м²",
            apartment.getAddress(),
            formatPrice(apartment.getPrice()),
            apartment.getFloor(),
            apartment.getFloorsCount(),
            apartment.getRooms(),
            apartment.getArea()
        );
        
        if (apartment.getPhone() != null && !apartment.getPhone().isEmpty()) {
            importantInfo += "\n📞 Телефон: " + apartment.getPhone();
        }
        
        // Додаємо опис, якщо є
        if (apartment.getDescription() != null && !apartment.getDescription().isEmpty()) {
            // Розраховуємо скільки символів можемо використати для опису
            int headerLength = sb.length();
            int importantInfoLength = importantInfo.length();
            int maxDescriptionLength = 1024 - headerLength - importantInfoLength - 20; // 20 для запасів
            
            if (maxDescriptionLength > 50) { // Мінімальна довжина для опису
                String description = apartment.getDescription();
                
                if (description.length() > maxDescriptionLength) {
                    // Обрізаємо по останньому повному реченню
                    description = description.substring(0, maxDescriptionLength);
                    int lastDot = description.lastIndexOf(".");
                    int lastExcl = description.lastIndexOf("!");
                    int lastQuest = description.lastIndexOf("?");
                    int lastSentence = Math.max(lastDot, Math.max(lastExcl, lastQuest));
                    
                    if (lastSentence > 30) { // Якщо знайшли речення не на початку
                        description = description.substring(0, lastSentence + 1);
                    } else {
                        // Якщо не знайшли речення, обрізаємо по останньому пробілу
                        int lastSpace = description.lastIndexOf(" ");
                        if (lastSpace > 50) {
                            description = description.substring(0, lastSpace) + "...";
                        } else {
                            description = description + "...";
                        }
                    }
                }
                
                sb.append("📝 Опис: ").append(description).append("\n\n");
            }
        }
        
        sb.append(importantInfo);
        
        String result = sb.toString();
        
        // Фінальна перевірка - якщо текст все ще занадто довгий, обрізаємо його
        if (result.length() > 1024) {
            // Залишаємо тільки заголовок і важливу інформацію
            String essentialInfo = "НОВА КВАРТИРА ДЛЯ ОРЕНДИ\n\n" + importantInfo;
            if (essentialInfo.length() > 1024) {
                // Якщо навіть важлива інформація занадто довга, обрізаємо її
                return essentialInfo.substring(0, 1021) + "...";
            }
            return essentialInfo;
        }
        
        return result;
    }

    private void logWarn(String msg) {
        System.out.println(msg);
        try (java.io.FileWriter fw = new java.io.FileWriter("warnings.log", true)) {
            fw.write(java.time.LocalDateTime.now() + " " + msg + "\n");
        } catch (Exception e) {
            System.err.println("[LOG] Не вдалося записати у warnings.log: " + e.getMessage());
        }
    }
} 
