package org.example;

import java.sql.*;

public class SQLiteJDBC {

    public static void main(String[] args) {
//        createTable();

        // INSERT
//        insertApartment(33236838, "Здається затишна 1 кім. квартира в чудовій локації", "вул. Марка Вовчка, буд. ", 10000, 2, 4, 1, 30.0,
//                new String[]{"photos/33236836_1.jpg", "photos/33236836_2.jpg", "photos/33236836_3.jpg", "photos/33236836_4.jpg", "photos/33236836_5.jpg"});

//
//        // UPDATE
//        updateApartment(33236836, "Оновлений опис квартири", 12500);
//
//        // DELETE
//        deleteApartment(33236836);


        selectAll();
    }

    public static void createTable() {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db");
             Statement stmt = c.createStatement()) {

            String sql = "CREATE TABLE IF NOT EXISTS Apartments (" +
                    "ID INTEGER PRIMARY KEY NOT NULL, " +
                    "Description TEXT, Address TEXT, Price INT, Phone TEXT, " +
                    "Floor INT, FloorsCount INT, " +
                    "Rooms INT, Area REAL, " +
                    "Photo1 TEXT, Photo2 TEXT, Photo3 TEXT, Photo4 TEXT, Photo5 TEXT, " +
                    "Posted BOOLEAN DEFAULT 0, " +
                    "CreatedAt TEXT)";


            stmt.executeUpdate(sql);
            System.out.println("Table created successfully");

        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static void insertApartment(String table_name, int id, String desc, String address, int price, String phone,  int floor, int floors, int rooms, double area, String[] photos, String CreatedAt) {
        String sql = "INSERT OR IGNORE INTO  " + table_name +
                "(ID, Description, Address, Price, Phone, Floor, FloorsCount, Rooms, Area, " +
                "Photo1, Photo2, Photo3, Photo4, Photo5, Posted, CreatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";



        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db");
             PreparedStatement pstmt = c.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.setString(2, desc);
            pstmt.setString(3, address);
            pstmt.setInt(4, price);
            pstmt.setString(5, phone);
            pstmt.setInt(6, floor);
            pstmt.setInt(7, floors);
            pstmt.setInt(8, rooms);
            pstmt.setDouble(9, area);
            for (int i = 0; i < 5; i++) {
                pstmt.setString(10 + i, photos.length > i ? photos[i] : null);
            }

            pstmt.setBoolean(15, false);
            pstmt.setString(16, CreatedAt);

            pstmt.executeUpdate();


            System.out.println("Apartment inserted with ID: " + id);
            System.out.println("➡️ Вставка у таблицю: " + table_name);

        } catch (SQLException e) {
            System.err.println("Insert error: " + e.getMessage());
        }
    }

    public static void selectAll() {
        String sql = "SELECT * FROM Apartments";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db");
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.printf("\nID: %d\nОпис: %s\nАдреса: %s\nЦіна: %d грн\nПоверх: %d з %d\nКімнат: %d\nПлоща: %.1f м²\nФото: [%s, %s, %s, %s, %s]\n",
                        rs.getInt("ID"), rs.getString("Description"), rs.getString("Address"), rs.getInt("Price"),rs.getString("Phone"),
                        rs.getInt("Floor"), rs.getInt("FloorsCount"), rs.getInt("Rooms"), rs.getDouble("Area"),
                        rs.getString("Photo1"), rs.getString("Photo2"), rs.getString("Photo3"), rs.getString("Photo4"), rs.getString("Photo5"),
                        rs.getBoolean("Posted"));
            }

        } catch (SQLException e) {
            System.err.println("Select error: " + e.getMessage());
        }
    }

    public static void updateApartment(int id, String newDescription, int newPrice) {
        String sql = "UPDATE Apartments SET Description = ?, Price = ? WHERE ID = ?";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db");
             PreparedStatement pstmt = c.prepareStatement(sql)) {

            pstmt.setString(1, newDescription);
            pstmt.setInt(2, newPrice);
            pstmt.setInt(3, id);
            int affected = pstmt.executeUpdate();

            System.out.println("Updated " + affected + " row(s).");

        } catch (SQLException e) {
            System.err.println("Update error: " + e.getMessage());
        }
    }

    public static void deleteApartment(int id) {
        String sql = "DELETE FROM Apartments WHERE ID = ?";

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db");
             PreparedStatement pstmt = c.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            int affected = pstmt.executeUpdate();

            System.out.println("Deleted " + affected + " row(s).");

        } catch (SQLException e) {
            System.err.println("Delete error: " + e.getMessage());
        }
    }

    public static void deleteAllFrom(String tableName) {
        String sql = "DELETE FROM " + tableName;

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db");
             PreparedStatement pstmt = c.prepareStatement(sql)) {

            pstmt.executeUpdate();
            System.out.println("✅ Видалено всі записи з таблиці: " + tableName);

        } catch (SQLException e) {
            System.err.println("❌ Помилка видалення з таблиці " + tableName + ": " + e.getMessage());
        }
    }

}
