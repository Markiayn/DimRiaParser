package org.example.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

public class Apartment {
    private int id;
    private String description;
    private String address;
    private int price;
    private String phone;
    private int floor;
    private int floorsCount;
    private int rooms;
    private double area;
    private List<String> photoPaths;
    private boolean posted;
    private LocalDateTime createdAt;
    
    public Apartment() {
        this.photoPaths = new ArrayList<>();
        this.posted = false;
    }
    
    public Apartment(int id, String description, String address, int price, String phone,
                    int floor, int floorsCount, int rooms, double area, LocalDateTime createdAt) {
        this();
        this.id = id;
        this.description = description;
        this.address = address;
        this.price = price;
        this.phone = phone;
        this.floor = floor;
        this.floorsCount = floorsCount;
        this.rooms = rooms;
        this.area = area;
        this.createdAt = createdAt;
    }
    
    // Геттери
    public int getId() { return id; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public int getPrice() { return price; }
    public String getPhone() { return phone; }
    public int getFloor() { return floor; }
    public int getFloorsCount() { return floorsCount; }
    public int getRooms() { return rooms; }
    public double getArea() { return area; }
    public List<String> getPhotoPaths() { return photoPaths; }
    public boolean isPosted() { return posted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    // Сеттери
    public void setId(int id) { this.id = id; }
    public void setDescription(String description) { this.description = description; }
    public void setAddress(String address) { this.address = address; }
    public void setPrice(int price) { this.price = price; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setFloor(int floor) { this.floor = floor; }
    public void setFloorsCount(int floorsCount) { this.floorsCount = floorsCount; }
    public void setRooms(int rooms) { this.rooms = rooms; }
    public void setArea(double area) { this.area = area; }
    public void setPhotoPaths(List<String> photoPaths) { this.photoPaths = photoPaths; }
    public void setPosted(boolean posted) { this.posted = posted; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    // Допоміжні методи
    public void addPhotoPath(String photoPath) {
        if (photoPath != null && !photoPath.isEmpty()) {
            this.photoPaths.add(photoPath);
        }
    }
    
    public String[] getPhotoPathsArray() {
        return photoPaths.toArray(new String[0]);
    }
    
    @Override
    public String toString() {
        return String.format("Apartment{id=%d, address='%s', price=%d, rooms=%d, area=%.1f, photos=%d}",
                id, address, price, rooms, area, photoPaths.size());
    }
} 