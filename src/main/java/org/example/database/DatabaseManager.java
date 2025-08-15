package org.example.database;

import org.example.config.AppConfig;
import org.example.model.Apartment;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseManager {
    private static DatabaseManager instance;
    private final String databaseUrl;
    private final boolean verbose;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private DatabaseManager() {
        this.databaseUrl = AppConfig.getDatabaseUrl();
        this.verbose = AppConfig.isVerbose();
    }
    
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    public void createTable(String tableName) {
        String sql = String.format("CREATE TABLE IF NOT EXISTS %s (" +
                "ID INTEGER PRIMARY KEY NOT NULL, " +
                "Description TEXT, Address TEXT, Price INT, Phone TEXT, " +
                "Floor INT, FloorsCount INT, " +
                "Rooms INT, Area REAL, " +
                "Photo1 TEXT, Photo2 TEXT, Photo3 TEXT, Photo4 TEXT, Photo5 TEXT, " +
                "Photo6 TEXT, Photo7 TEXT, Photo8 TEXT, Photo9 TEXT, Photo10 TEXT, " +
                "Posted BOOLEAN DEFAULT 0, " +
                "CreatedAt TEXT)", tableName);
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Таблицю " + tableName + " створено успішно");
            
            // Перевіряємо чи потрібно додати нові поля для фото
            updateTableForNewPhotos(tableName);
        } catch (SQLException e) {
            System.err.println("Помилка створення таблиці " + tableName + ": " + e.getMessage());
            throw new RuntimeException("Не вдалося створити таблицю", e);
        }
    }
    
    private void updateTableForNewPhotos(String tableName) {
        try (Connection conn = getConnection()) {
            // Перевіряємо чи існують нові поля для фото
            String[] newPhotoColumns = {"Photo6", "Photo7", "Photo8", "Photo9", "Photo10"};
            
            for (String column : newPhotoColumns) {
                try {
                    // Спробуємо додати колонку
                    String alterSql = String.format("ALTER TABLE %s ADD COLUMN %s TEXT", tableName, column);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate(alterSql);
                        System.out.println("Додано колонку " + column + " до таблиці " + tableName);
                    }
                } catch (SQLException e) {
                    // Якщо колонка вже існує, ігноруємо помилку
                    if (verbose) {
                        System.out.println("Колонка " + column + " вже існує в таблиці " + tableName);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Помилка оновлення таблиці " + tableName + " для нових фото: " + e.getMessage());
        }
    }
    
    public void insertApartment(String tableName, Apartment apartment) {
        String sql = String.format("INSERT OR IGNORE INTO %s " +
                "(ID, Description, Address, Price, Phone, Floor, FloorsCount, Rooms, Area, " +
                "Photo1, Photo2, Photo3, Photo4, Photo5, Photo6, Photo7, Photo8, Photo9, Photo10, Posted, CreatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, apartment.getId());
            pstmt.setString(2, apartment.getDescription());
            pstmt.setString(3, apartment.getAddress());
            pstmt.setInt(4, apartment.getPrice());
            pstmt.setString(5, apartment.getPhone());
            pstmt.setInt(6, apartment.getFloor());
            pstmt.setInt(7, apartment.getFloorsCount());
            pstmt.setInt(8, apartment.getRooms());
            pstmt.setDouble(9, apartment.getArea());
            
            String[] photos = apartment.getPhotoPathsArray();
            for (int i = 0; i < 10; i++) {
                pstmt.setString(10 + i, photos.length > i ? photos[i] : null);
            }
            
            pstmt.setBoolean(20, apartment.isPosted());
            pstmt.setString(21, apartment.getCreatedAt().format(formatter));
            
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                System.out.println("Квартиру з ID " + apartment.getId() + " додано до таблиці " + tableName);
            } else {
                System.out.println("Квартира з ID " + apartment.getId() + " вже існує в таблиці " + tableName);
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка вставки квартири: " + e.getMessage());
            throw new RuntimeException("Не вдалося вставити квартиру", e);
        }
    }
    
    public List<Apartment> getUnpostedApartments(String tableName, int limit) {
        String sql = String.format("SELECT * FROM %s WHERE Posted = 0 ORDER BY CreatedAt DESC LIMIT ?", tableName);
        List<Apartment> apartments = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                apartments.add(mapResultSetToApartment(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка отримання неопублікованих квартир: " + e.getMessage());
        }
        
        return apartments;
    }
    
    public List<Apartment> getUnpostedApartmentsFromLastHour(String tableName, int limit) {
        String sql = String.format(
            "SELECT * FROM %s WHERE Posted = 0 AND CreatedAt >= ? ORDER BY CreatedAt DESC LIMIT ?", 
            tableName
        );
        List<Apartment> apartments = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            String oneHourAgoStr = oneHourAgo.format(formatter);
            
            pstmt.setString(1, oneHourAgoStr);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                apartments.add(mapResultSetToApartment(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка отримання квартир з останньої години: " + e.getMessage());
        }
        
        return apartments;
    }
    
    public List<Apartment> getUnpostedApartments() {
        return getUnpostedApartments(AppConfig.getTableName(), 10);
    }
    
    public List<Apartment> getUnpostedApartmentsFromLastHour() {
        return getUnpostedApartmentsFromLastHour(AppConfig.getTableName(), 10);
    }
    
    public List<Apartment> getUnpostedApartmentsFromLast24Hours(String tableName, int limit) {
        String sql = String.format(
            "SELECT * FROM %s WHERE Posted = 0 AND CreatedAt >= ? ORDER BY CreatedAt DESC LIMIT ?",
            tableName
        );
        List<Apartment> apartments = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
            String dayAgoStr = dayAgo.format(formatter);
            
            pstmt.setString(1, dayAgoStr);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                apartments.add(mapResultSetToApartment(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка отримання квартир з останніх 24 годин: " + e.getMessage());
        }
        return apartments;
    }
    
    public void markApartmentAsPublished(int id) {
        markAsPosted(AppConfig.getTableName(), id);
    }
    
    public Optional<Apartment> getApartmentById(String tableName, int id) {
        String sql = String.format("SELECT * FROM %s WHERE ID = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return Optional.of(mapResultSetToApartment(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка отримання квартири за ID: " + e.getMessage());
        }
        
        return Optional.empty();
    }
    
    public void markAsPosted(String tableName, int id) {
        String sql = String.format("UPDATE %s SET Posted = 1 WHERE ID = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int affected = pstmt.executeUpdate();
            
            if (affected > 0) {
                System.out.println("✅ Квартиру з ID " + id + " у таблиці " + tableName + " позначено як опубліковану");
            } else {
                System.out.println("⚠️ Квартиру з ID " + id + " у таблиці " + tableName + " не знайдено");
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка оновлення статусу: " + e.getMessage());
        }
    }
    
    public void deleteAllFromTable(String tableName) {
        String sql = String.format("DELETE FROM %s", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            int affected = pstmt.executeUpdate();
            System.out.println("✅ Видалено " + affected + " записів з таблиці: " + tableName);
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка видалення з таблиці " + tableName + ": " + e.getMessage());
        }
    }
    
    public void deleteApartment(String tableName, int id) {
        String sql = String.format("DELETE FROM %s WHERE ID = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int affected = pstmt.executeUpdate();
            
            if (affected > 0) {
                System.out.println("✅ Квартиру з ID " + id + " видалено з таблиці " + tableName);
            } else {
                System.out.println("⚠️ Квартиру з ID " + id + " у таблиці " + tableName + " не знайдено");
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка видалення квартири: " + e.getMessage());
        }
    }
    
    public void clearTable(String tableName) {
        String sql = "DELETE FROM " + tableName;
        try (java.sql.Connection conn = getConnection(); java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Таблицю " + tableName + " очищено.");
        } catch (Exception e) {
            System.err.println("Помилка очищення таблиці " + tableName + ": " + e.getMessage());
        }
    }
    
    private Apartment mapResultSetToApartment(ResultSet rs) throws SQLException {
        Apartment apartment = new Apartment();
        apartment.setId(rs.getInt("ID"));
        apartment.setDescription(rs.getString("Description"));
        apartment.setAddress(rs.getString("Address"));
        apartment.setPrice(rs.getInt("Price"));
        apartment.setPhone(rs.getString("Phone"));
        apartment.setFloor(rs.getInt("Floor"));
        apartment.setFloorsCount(rs.getInt("FloorsCount"));
        apartment.setRooms(rs.getInt("Rooms"));
        apartment.setArea(rs.getDouble("Area"));
        apartment.setPosted(rs.getBoolean("Posted"));
        
        // Додаємо фотографії
        for (int i = 1; i <= 10; i++) {
            String photo = rs.getString("Photo" + i);
            if (photo != null && !photo.isEmpty()) {
                apartment.addPhotoPath(photo);
            }
        }
        
        // Парсимо дату створення
        String createdAtStr = rs.getString("CreatedAt");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            try {
                apartment.setCreatedAt(LocalDateTime.parse(createdAtStr, formatter));
            } catch (Exception e) {
                System.err.println("⚠️ Помилка парсингу дати: " + createdAtStr);
            }
        }
        
        return apartment;
    }
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }
    
    // Методи для статистики
    public int getTotalApartmentsCount(String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка отримання загальної кількості квартир: " + e.getMessage());
        }
        
        return 0;
    }
    
    public int getPostedApartmentsCount(String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE Posted = 1", tableName);
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка отримання кількості опублікованих квартир: " + e.getMessage());
        }
        
        return 0;
    }
    
    public int getUnpostedApartmentsCount(String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE Posted = 0", tableName);
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка отримання кількості неопублікованих квартир: " + e.getMessage());
        }
        
        return 0;
    }
    
    public int getNewApartmentsCount(String tableName, int hoursBack) {
        String sql = String.format(
            "SELECT COUNT(*) FROM %s WHERE CreatedAt >= ?", 
            tableName
        );
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            LocalDateTime timeBack = LocalDateTime.now().minusHours(hoursBack);
            String timeBackStr = timeBack.format(formatter);
            
            pstmt.setString(1, timeBackStr);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1);
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка отримання кількості нових квартир: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Перевіряє чи існує квартира з вказаним ID в таблиці
     * @param tableName назва таблиці
     * @param apartmentId ID квартири
     * @return true якщо квартира існує, false якщо ні
     */
    public boolean apartmentExists(String tableName, int apartmentId) {
        String sql = String.format("SELECT COUNT(*) FROM %s WHERE ID = ?", tableName);
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, apartmentId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("Помилка перевірки існування квартири " + apartmentId + " в таблиці " + tableName + ": " + e.getMessage());
        }
        
        return false;
    }
    
    public void printStatisticsForCity(String tableName, String cityName) {
        int total = getTotalApartmentsCount(tableName);
        int posted = getPostedApartmentsCount(tableName);
        int unposted = getUnpostedApartmentsCount(tableName);
        int newLastHour = getNewApartmentsCount(tableName, 1);
        
        System.out.println("📊 СТАТИСТИКА для міста " + cityName + " (" + tableName + "):");
        System.out.println("   🏠 Загалом в БД: " + total);
        System.out.println("   ✅ Опубліковано: " + posted);
        System.out.println("   ⏳ Очікують публікації: " + unposted);
        System.out.println("   🆕 Нові (за останню годину): " + newLastHour);
        System.out.println("   📈 Прогрес: " + (total > 0 ? String.format("%.1f%%", (double) posted / total * 100) : "0%"));
    }
    
    public void printStatisticsForAllCities() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 СТАТИСТИКА ПО ВСІХ МІСТАХ");
        System.out.println("=".repeat(60));
        
        int totalAll = 0;
        int postedAll = 0;
        int unpostedAll = 0;
        int newAll = 0;
        
        for (org.example.config.CityConfig.City city : org.example.config.CityConfig.getCities()) {
            int total = getTotalApartmentsCount(city.dbTable);
            int posted = getPostedApartmentsCount(city.dbTable);
            int unposted = getUnpostedApartmentsCount(city.dbTable);
            int newLastHour = getNewApartmentsCount(city.dbTable, 1);
            
            totalAll += total;
            postedAll += posted;
            unpostedAll += unposted;
            newAll += newLastHour;
            
            printStatisticsForCity(city.dbTable, city.name);
        }
        
        System.out.println("\n📊 ПІДСУМОК ПО ВСІХ МІСТАХ:");
        System.out.println("   🏠 Загалом в БД: " + totalAll);
        System.out.println("   ✅ Опубліковано: " + postedAll);
        System.out.println("   ⏳ Очікують публікації: " + unpostedAll);
        System.out.println("   🆕 Нові (за останню годину): " + newAll);
        System.out.println("   📈 Загальний прогрес: " + (totalAll > 0 ? String.format("%.1f%%", (double) postedAll / totalAll * 100) : "0%"));
        System.out.println("=".repeat(60));
    }
} 