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
    
    public boolean sendApartmentPost(Apartment apartment, String chatId) {
        try {
            if (verbose) {
                System.out.println("[TELEGRAM] Спроба відправки квартири " + apartment.getId() + " в канал: " + chatId);
            }
            
            String message = formatApartmentMessagePlain(apartment);
            if (verbose) {
                System.out.println("[TELEGRAM] Повідомлення для квартири " + apartment.getId() + " (довжина: " + message.length() + " символів)");
            }
            
            java.util.List<String> photos = apartment.getPhotoPaths();

            if (photos == null || photos.isEmpty()) {
                if (verbose) {
                    System.out.println("[TELEGRAM] Квартира " + apartment.getId() + " не має фото");
                }
                return false;
            }
            if (chatId == null || chatId.isEmpty()) {
                if (verbose) {
                    System.out.println("[TELEGRAM] Не вказано канал для квартири " + apartment.getId());
                }
                return false;
            }
            
            if (message.length() > 1024) {
                logWarn("[TELEGRAM] Повідомлення для квартири " + apartment.getId() + " було обрізано з " + message.length() + " до 1024 символів");
            }
            
            boolean result = false;
            if (photos.size() == 1) {
                if (verbose) {
                    System.out.println("[TELEGRAM] Відправляємо 1 фото для квартири " + apartment.getId());
                }
                result = sendMessageWithPhoto(chatId, message, photos.get(0), apartment.getId());
            } else if (photos.size() > 1) {
                if (verbose) {
                    System.out.println("[TELEGRAM] Відправляємо " + photos.size() + " фото для квартири " + apartment.getId());
                }
                result = sendMediaGroup(chatId, message, photos, apartment.getId());
            } else {
                if (verbose) {
                    System.out.println("[TELEGRAM] Немає фото для квартири " + apartment.getId());
                }
            }
            
            if (verbose) {
                System.out.println("[TELEGRAM] Результат відправки квартири " + apartment.getId() + ": " + (result ? "УСПІШНО" : "НЕ ВДАЛОСЯ"));
            }
            return result;
        } catch (Exception e) {
            logWarn("[TELEGRAM] Помилка відправки квартири " + apartment.getId() + " в канал " + chatId + ": " + e.getMessage());
            if (verbose) {
                System.out.println("[TELEGRAM] Деталі помилки: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }
    
    public boolean sendToBothChannels(Apartment apartment) {
        boolean success1 = sendApartmentPost(apartment, chatId1);
        boolean success2 = sendApartmentPost(apartment, chatId2);
        
        return success1 || success2;
    }
    
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
    
    private boolean sendMessageWithPhoto(String chatId, String message, String photoPath, int apartmentId) {
        try {
            java.io.File photoFile = new java.io.File(photoPath);
            if (!photoFile.exists()) {
                if (verbose) {
                    System.out.println("[TELEGRAM] Файл фото не існує: " + photoPath);
                }
                return false;
            }
            if (verbose) {
                System.out.println("[TELEGRAM] Файл фото знайдено: " + photoPath + " (розмір: " + photoFile.length() + " байт)");
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
                
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n");
                writer.append("\r\n");
                writer.append(chatId).append("\r\n");
                
                if (message != null && !message.isEmpty()) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"caption\"").append("\r\n");
                    writer.append("\r\n");
                    writer.append(message).append("\r\n");
                }
                
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
                boolean success = responseJson.optBoolean("ok", false);
                if (verbose) {
                    System.out.println("[TELEGRAM] Відповідь API для квартири " + apartmentId + ": " + response.toString());
                }
                return success;
            } else {
                String errorResponse = response.toString();
                logWarn("[TELEGRAM] Помилка відправки фото для квартири " + apartmentId + " в канал " + chatId + " (код " + responseCode + "): " + errorResponse);
                if (verbose) {
                    System.out.println("[TELEGRAM] Деталі помилки HTTP " + responseCode + ": " + errorResponse);
                }
                return false;
            }
            
        } catch (Exception e) {
            logWarn("[TELEGRAM] Виняток при відправці фото для квартири " + apartmentId + ": " + e.getMessage());
            return false;
        }
    }
    
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
                System.out.println("Telegram бот підключено: @" + result.getString("username"));
            }
            
            return isOk;
            
        } catch (Exception e) {
            System.err.println("Помилка підключення до Telegram: " + e.getMessage());
            return false;
        }
    }
    
    public boolean testChannelAccess(String chatId) {
        try {
            String url = String.format("https://api.telegram.org/bot%s/getChat", botToken);
            org.json.JSONObject requestBody = new org.json.JSONObject();
            requestBody.put("chat_id", chatId);
            
            String response = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .requestBody(requestBody.toString())
                    .header("Content-Type", "application/json")
                    .post()
                    .body()
                    .text();
            
            org.json.JSONObject responseJson = new org.json.JSONObject(response);
            boolean success = responseJson.getBoolean("ok");
            
            if (success && verbose) {
                org.json.JSONObject chat = responseJson.getJSONObject("result");
                System.out.println("[TELEGRAM] Доступ до каналу " + chatId + " підтверджено: " + chat.getString("title"));
            } else if (verbose) {
                System.out.println("[TELEGRAM] Немає доступу до каналу " + chatId + ": " + response);
            }
            
            return success;
        } catch (Exception e) {
            if (verbose) {
                System.out.println("[TELEGRAM] Помилка тестування каналу " + chatId + ": " + e.getMessage());
            }
            return false;
        }
    }
    
    private boolean sendMediaGroup(String chatId, String message, java.util.List<String> photoPaths, int apartmentId) {
        try {
            if (verbose) {
                System.out.println("[TELEGRAM] Початок відправки MediaGroup для квартири " + apartmentId + " в канал " + chatId);
                System.out.println("[TELEGRAM] Кількість фото: " + photoPaths.size());
            }
            
            java.util.List<java.io.File> photoFiles = new java.util.ArrayList<>();
            for (String path : photoPaths) {
                java.io.File f = new java.io.File(path);
                if (f.exists()) {
                    photoFiles.add(f);
                    if (verbose) {
                        System.out.println("[TELEGRAM] Файл знайдено: " + path + " (" + f.length() + " байт)");
                    }
                } else {
                    if (verbose) {
                        System.out.println("[TELEGRAM] Файл не знайдено: " + path);
                    }
                }
            }
            
            if (photoFiles.size() < 2) {
                if (verbose) {
                    System.out.println("[TELEGRAM] Недостатньо файлів для MediaGroup: " + photoFiles.size());
                }
                return false;
            }
            
            String url = String.format("https://api.telegram.org/bot%s/sendMediaGroup", botToken);
            String boundary = "*****" + System.currentTimeMillis() + "*****";
            
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
                
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chat_id\"").append("\r\n");
                writer.append("\r\n");
                writer.append(chatId).append("\r\n");
                
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"media\"").append("\r\n");
                writer.append("\r\n");
                writer.append(mediaArray.toString()).append("\r\n");
                
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
                boolean success = responseJson.optBoolean("ok", false);
                if (verbose) {
                    System.out.println("[TELEGRAM] MediaGroup відповідь для квартири " + apartmentId + ": " + response.toString());
                }
                return success;
            } else {
                if (verbose) {
                    System.out.println("[TELEGRAM] MediaGroup помилка HTTP " + responseCode + " для квартири " + apartmentId + ": " + response.toString());
                }
                return false;
            }
            
        } catch (Exception e) {
            if (verbose) {
                System.out.println("[TELEGRAM] MediaGroup виняток для квартири " + apartmentId + ": " + e.getMessage());
            }
            return false;
        }
    }

    private String formatApartmentMessagePlain(Apartment apartment) {
        StringBuilder sb = new StringBuilder();
        sb.append("НОВА КВАРТИРА ДЛЯ ОРЕНДИ\n\n");
        
        String importantInfo = String.format(
            "📍 Адреса: %s\n" +
            "💵 Ціна: %s\n" +
            "🏢 Поверх: %d/%d\n" +
            "🛏 Кімнат: %d\n" +
            "📐 Площа: %.1f м²",
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
        
        if (apartment.getDescription() != null && !apartment.getDescription().isEmpty()) {
            int headerLength = sb.length();
            int importantInfoLength = importantInfo.length();
            int maxDescriptionLength = 1024 - headerLength - importantInfoLength - 20;
            
            if (maxDescriptionLength > 50) {
                String description = apartment.getDescription();
                
                if (description.length() > maxDescriptionLength) {
                    description = description.substring(0, maxDescriptionLength);
                    int lastDot = description.lastIndexOf(".");
                    int lastExcl = description.lastIndexOf("!");
                    int lastQuest = description.lastIndexOf("?");
                    int lastSentence = Math.max(lastDot, Math.max(lastExcl, lastQuest));
                    
                    if (lastSentence > 30) {
                        description = description.substring(0, lastSentence + 1);
                    } else {
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
        
        if (result.length() > 1024) {
            String essentialInfo = "НОВА КВАРТИРА ДЛЯ ОРЕНДИ\n\n" + importantInfo;
            if (essentialInfo.length() > 1024) {
                return essentialInfo.substring(0, 1021) + "...";
            }
            return essentialInfo;
        }
        
        return result;
    }
    
    private String formatPrice(int price) {
        if (price < 2000) {
            return String.format("%d $", price);
        } else {
            return String.format("%d грн/міс", price);
        }
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
