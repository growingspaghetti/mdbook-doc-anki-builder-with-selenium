package growingspaghetti.selenium_screenshooter;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.Screenshot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;

public class App {
    static final String NEXT_XPATH = "//a[@rel='next']";
    static final By HEADERS = By.xpath(
            "//*[name()='h1' or name()='h2' or name()='h3' or name()='h4' or name()='h5']");

    static String fileName(String s, String dir) {
        return dir + "_" + StringUtils.substringAfterLast(s, "/");
    }

    static String pageTitle(String s) {
        return StringUtils.substringBefore(s, " - ").trim();
    }

    static String subScreenshotName(File fullScreenshot, String dir, int num) {
        return fullScreenshot.getName().replace(".png", "") + String.format("_%04d.png", num);
    }

    static void useSelenium(String dir) throws IOException {
        new File("rustbook/" + dir).mkdir();
        List<String> results = new ArrayList<>();

        WebDriver driver =
                new RemoteWebDriver(new URL("http://127.0.0.1:4444/wd/hub"), DesiredCapabilities.chrome());
        try {
            driver.manage().window().setSize(new Dimension(1000, 1200));
            driver.get(String.format("https://doc.rust-lang.org/%s/index.html", dir));
            List<WebElement> ancs = driver.findElements(By.xpath(NEXT_XPATH));
            while (!ancs.isEmpty()) {
                System.out.println("downloading " + driver.getCurrentUrl());
                Screenshot screenshot =
                        new AShot()
                                .shootingStrategy(ShootingStrategies.viewportPasting(1000))
                                .takeScreenshot(driver);

                File fullScreenshot =
                        new File("rustbook/" + fileName(driver.getCurrentUrl(), dir) + ".png");
                ImageIO.write(screenshot.getImage(), "PNG", fullScreenshot);
                List<WebElement> headers = driver.findElements(HEADERS);

                splitImages(dir, fullScreenshot, headers);
                for (int i = 1; i < headers.size(); i++) {
                    String imgName = subScreenshotName(fullScreenshot, dir, i);
                    WebElement header = headers.get(i);
                    String title = header.getText();
                    title += " ← " + pageTitle(driver.getTitle());
                    String line = String.format("%s\t<img src=\"mdbook/%s/%s\">", title, dir, imgName);
                    results.add(line);
                }

                ancs.get(0).click();
                ancs = driver.findElements(By.xpath(NEXT_XPATH));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            driver.quit();
        }
        FileUtils.writeLines(new File(dir + "_rust-doc-anki.html"), results);
    }

    static void splitImages(String dir, File fullScreenshot, List<WebElement> headers)
            throws IOException {
        BufferedImage fullImg = ImageIO.read(fullScreenshot);
        List<Integer> points = headers.stream().map(h -> h.getRect().y).collect(Collectors.toList());
        for (int i = 1; i < points.size(); i++) {
            int y = points.get(i);
            int h = 0;
            if (i == points.size() - 1) {
                h = fullImg.getHeight() - y;
            } else {
                h = points.get(i + 1) - y;
            }
            BufferedImage sub = fullImg.getSubimage(0, y, fullImg.getWidth(), h);
            String imgName = subScreenshotName(fullScreenshot, dir, i);
            ImageIO.write(sub, "png", new File("rustbook/" + dir + "/" + imgName));
        }
    }

    static void runImageMagick(String book) {
        Map<String, String> westEast = new HashMap<>();
        westEast.put("East", "120x0");
        westEast.put("West", "102x0");
        for (Map.Entry<String, String> entry : westEast.entrySet()) {
            List<String> cmd = new ArrayList<>();
            cmd.add("mogrify");
            cmd.add("-gravity");
            cmd.add(entry.getKey());
            cmd.add("-chop");
            cmd.add(entry.getValue());
            cmd.add("rustbook/" + book + "/*.png");
            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectErrorStream(true);
            try {
                Process process = processBuilder.start();
                try (BufferedReader bufferedReader =
                             new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    StringBuilder stringBuilder = new StringBuilder();
                    String string;
                    while ((string = bufferedReader.readLine()) != null) {
                        stringBuilder.append(string).append("\n");
                    }
                    System.out.println(stringBuilder.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String[] books =
                new String[]{
                        "book",
                        "rust-by-example",
                        "cargo",
                        "nomicon",
                        "rustc"
                };
        new File("rustbook").mkdir();
        for (String book : books) {
            useSelenium(book);
            runImageMagick(book);
        }
    }
}
