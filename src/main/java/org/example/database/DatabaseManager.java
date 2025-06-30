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
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private DatabaseManager() {
        this.databaseUrl = AppConfig.getDatabaseUrl();
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
                "Posted BOOLEAN DEFAULT 0, " +
                "CreatedAt TEXT)", tableName);
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("✅ Таблицю " + tableName + " створено успішно");
        } catch (SQLException e) {
            System.err.println("❌ Помилка створення таблиці " + tableName + ": " + e.getMessage());
            throw new RuntimeException("Не вдалося створити таблицю", e);
        }
    }
    
    public void insertApartment(String tableName, Apartment apartment) {
        String sql = String.format("INSERT OR IGNORE INTO %s " +
                "(ID, Description, Address, Price, Phone, Floor, FloorsCount, Rooms, Area, " +
                "Photo1, Photo2, Photo3, Photo4, Photo5, Posted, CreatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", tableName);
        
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
            for (int i = 0; i < 5; i++) {
                pstmt.setString(10 + i, photos.length > i ? photos[i] : null);
            }
            
            pstmt.setBoolean(15, apartment.isPosted());
            pstmt.setString(16, apartment.getCreatedAt().format(formatter));
            
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                System.out.println("✅ Квартиру з ID " + apartment.getId() + " додано до таблиці " + tableName);
            } else {
                System.out.println("⚠️ Квартира з ID " + apartment.getId() + " вже існує в таблиці " + tableName);
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка вставки квартири: " + e.getMessage());
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
    
    /**
     * Отримує неопубліковані квартири з останньої години
     */
    public List<Apartment> getUnpostedApartmentsFromLastHour(String tableName, int limit) {
        String sql = String.format(
            "SELECT * FROM %s WHERE Posted = 0 AND CreatedAt >= ? ORDER BY CreatedAt DESC LIMIT ?", 
            tableName
        );
        List<Apartment> apartments = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Встановлюємо час - 1 година тому
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            String oneHourAgoStr = oneHourAgo.format(formatter);
            
            pstmt.setString(1, oneHourAgoStr);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                apartments.add(mapResultSetToApartment(rs));
            }
            
        } catch (SQLException e) {
            System.err.println("❌ Помилка отримання квартир з останньої години: " + e.getMessage());
        }
        
        return apartments;
    }
    
    /**
     * Отримує неопубліковані квартири (без параметра tableName)
     */
    public List<Apartment> getUnpostedApartments() {
        return getUnpostedApartments(AppConfig.getTableName(), 10);
    }
    
    /**
     * Отримує неопубліковані квартири з останньої години (без параметра tableName)
     */
    public List<Apartment> getUnpostedApartmentsFromLastHour() {
        return getUnpostedApartmentsFromLastHour(AppConfig.getTableName(), 10);
    }
    
    /**
     * Позначає квартиру як опубліковану (без параметра tableName)
     */
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
        for (int i = 1; i <= 5; i++) {
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
} 