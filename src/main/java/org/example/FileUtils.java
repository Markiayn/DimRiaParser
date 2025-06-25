package org.example;


import java.io.File;

public class FileUtils {

    public static void deleteAllPhotos(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            System.out.println("📁 Папка " + folderPath + " не існує.");
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("📂 Папка порожня або не вдалося прочитати файли.");
            return;
        }

        int deletedCount = 0;
        for (File file : files) {
            if (file.isFile()) {
                if (file.delete()) {
                    deletedCount++;
                } else {
                    System.out.println("❌ Не вдалося видалити: " + file.getName());
                }
            }
        }

        System.out.println("🧹 Видалено " + deletedCount + " фото з папки: " + folderPath);
    }
}
