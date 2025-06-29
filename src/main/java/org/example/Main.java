package org.example;

import org.example.SQLiteJDBC;
import org.example.FileUtils;

import static org.example.Parser.parseRiaApartments;

public class Main {

    public static void main(String[] args) throws Exception {

        SQLiteJDBC  sql = new SQLiteJDBC();
        FileUtils fileUtils = new FileUtils();

        sql.deleteAllFrom("Apartments");
        sql.deleteAllFrom("Apartments_Lviv");
        sql.deleteAllFrom("Apartments_IvanoFrankivsk");
        fileUtils.deleteAllPhotos("photos");

        //Lviv
        parseRiaApartments(
                "Apartments_Lviv",
                "src/main/java/org/example/chromedriver-win64/chromedriver.exe",
                5,        // область (Львівська)
                null,     // місто (null якщо не потрібно)
                2,        // тип нерухомості (2 = квартира)
                3,        // тип операції (3 = оренда)
                48,       // години ліміту по публікації
                2,        // кількість сторінок
                1,        // мін. кімнат
                25.0,     // мін. площа
                5,        // макс. фото
                true      // verbose
        );


        //Frankivsk
        parseRiaApartments(
                "Apartments_IvanoFrankivsk",
                "src/main/java/org/example/chromedriver-win64/chromedriver.exe",
                15,        // область (Івано*Франківська)
                null,     // місто (null якщо не потрібно)
                2,        // тип нерухомості (2 = квартира)
                3,        // тип операції (3 = оренда)
                48,       // години ліміту по публікації
                2,        // кількість сторінок
                1,        // мін. кімнат
                25.0,     // мін. площа
                5,        // макс. фото
                true      // verbose
        );

//        TelegramPostDispatcher dispatcher = new TelegramPostDispatcher();
//        dispatcher.dispatchPosts(2); // або скільки хочеш постів на місто

    }
}