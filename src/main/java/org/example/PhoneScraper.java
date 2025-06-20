package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.Duration;

public class PhoneScraper {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "D:\\Markiyan\\Programming\\2025\\DimRiaParser\\src\\main\\java\\org\\example\\chromedriver-win64\\chromedriver.exe");
        WebDriver driver = new ChromeDriver();

        try {
            driver.get("https://dom.ria.com/uk/realty-dolgosrochnaya-arenda-kvartira-lvov-kleparov-kleparovskaya-ulitsa-33209486.html");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Знайти div, де показується номер телефону
            WebElement phoneElement = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div[data-tm='phone']"))
            );

            String phoneText = phoneElement.getText();
            System.out.println("📞 Номер телефону: " + phoneText);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}

