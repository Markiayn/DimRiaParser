package org.example;



import static org.example.Parser.parseRiaApartments;

public class Main {

    public static void main(String[] args) throws Exception {
        parseRiaApartments(
                "src/main/java/org/example/chromedriver-win64/chromedriver.exe",
                5,        // область (Львівська)
                null,     // місто (null якщо не потрібно)
                2,        // тип нерухомості (2 = квартира)
                3,        // тип операції (3 = оренда)
                24,       // години ліміту по публікації
                2,        // кількість сторінок
                1,        // мін. кімнат
                25.0,     // мін. площа
                5,        // макс. фото
                true      // verbose
        );
    }
    }