package org.example.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class FileUtils {

    /**
     * Видаляє всі файли з вказаної папки
     */
    public static void deleteAllPhotos(String folderPath) {
        File folder = new File(folderPath);
        
        if (!folder.exists()) {
            System.out.println("Папка " + folderPath + " не існує.");
            return;
        }

        if (!folder.isDirectory()) {
            System.out.println(folderPath + " не є папкою.");
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("Папка порожня.");
            return;
        }

        int deletedCount = 0;
        int failedCount = 0;
        
        for (File file : files) {
            if (file.isFile()) {
                if (file.delete()) {
                    deletedCount++;
                } else {
                    failedCount++;
                    System.out.println("Не вдалося видалити: " + file.getName());
                }
            }
        }

        System.out.println("Видалено " + deletedCount + " файлів з папки: " + folderPath);
        if (failedCount > 0) {
            System.out.println("Не вдалося видалити " + failedCount + " файлів");
        }
    }

    /**
     * Створює папку якщо вона не існує
     */
    public static void createDirectoryIfNotExists(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Створено папку: " + directoryPath);
            }
        } catch (IOException e) {
            System.err.println("Помилка створення папки " + directoryPath + ": " + e.getMessage());
        }
    }

    /**
     * Отримує розмір папки в байтах
     */
    public static long getDirectorySize(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                return 0;
            }

            try (Stream<Path> paths = Files.walk(path)) {
                return paths
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            }
        } catch (IOException e) {
            System.err.println("Помилка обчислення розміру папки: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Форматує розмір файлу в читабельному вигляді
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Отримує кількість файлів у папці
     */
    public static int getFileCount(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path)) {
                return 0;
            }

            try (Stream<Path> paths = Files.walk(path)) {
                return (int) paths.filter(Files::isRegularFile).count();
            }
        } catch (IOException e) {
            System.err.println("Помилка підрахунку файлів: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Видаляє файл з обробкою помилок
     */
    public static boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                return true;
            }
            return false;
        } catch (IOException e) {
            System.err.println("Помилка видалення файлу " + filePath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Перевіряє чи існує файл
     */
    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * Отримує розширення файлу
     */
    public static String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Перевіряє чи є файл зображенням
     */
    public static boolean isImageFile(String fileName) {
        String extension = getFileExtension(fileName);
        return extension.matches("(jpg|jpeg|png|gif|webp|bmp)");
    }
} 