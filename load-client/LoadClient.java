import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class LoadClient {

    private static final String BASE_URL = "http://nginx";
    private static final String SHORTEN_URL = BASE_URL + "/api/v1/data/shorten";
    private static final String REDIRECT_URL = BASE_URL + "/api/v1";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final Random random = new Random();
    private static final List<String> shortUrls = new CopyOnWriteArrayList<>();

    private static final AtomicLong shortenOk = new AtomicLong();
    private static final AtomicLong shortenErr = new AtomicLong();
    private static final AtomicLong redirectOk = new AtomicLong();
    private static final AtomicLong redirectErr = new AtomicLong();
    private static final AtomicLong total = new AtomicLong();

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(Redirect.NEVER)
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("Load client starting... waiting for services to be ready");
        Thread.sleep(10_000);

        System.out.println("Starting load test (1:10 shorten:redirect ratio)");
        long iteration = 0;
        while (true) {
            if (iteration % 11 == 0) {
                shorten();
            } else {
                redirect();
            }

            long count = total.incrementAndGet();
            iteration++;

            if (count % 100 == 0) {
                System.out.printf("[%d] shorten ok=%d err=%d | redirect ok=%d err=%d | cached urls=%d%n",
                        count, shortenOk.get(), shortenErr.get(),
                        redirectOk.get(), redirectErr.get(), shortUrls.size());
            }

            Thread.sleep(50);
        }
    }

    private static void shorten() {
        String longUrl = randomUrl();
        try {
            String encoded = URLEncoder.encode(longUrl, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SHORTEN_URL + "?longUrl=" + encoded))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                shortUrls.add(response.body().strip());
                shortenOk.incrementAndGet();
            } else {
                shortenErr.incrementAndGet();
            }
        } catch (Exception e) {
            shortenErr.incrementAndGet();
            System.err.println("Shorten error: " + e.getMessage());
        }
    }

    private static void redirect() {
        if (shortUrls.isEmpty()) {
            shorten();
            return;
        }
        String shortUrl = shortUrls.get(random.nextInt(shortUrls.size()));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REDIRECT_URL + "/" + shortUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 301) {
                redirectOk.incrementAndGet();
            } else {
                redirectErr.incrementAndGet();
            }
        } catch (Exception e) {
            redirectErr.incrementAndGet();
            System.err.println("Redirect error: " + e.getMessage());
        }
    }

    private static String randomUrl() {
        String domain = randomString(4 + random.nextInt(7));
        String path = randomString(5 + random.nextInt(16));
        return "https://www." + domain + ".com/" + path;
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
