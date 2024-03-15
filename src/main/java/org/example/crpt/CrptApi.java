package org.example.crpt;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private static final String API_URI = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final Logger log = LoggerFactory.getLogger(CrptApi.class);
    private TimeUnit timeUnit;
    private int requestLimit;
    private LocalDateTime limitDateTime;
    private AtomicInteger requestCount;
    private Lock lock = new ReentrantLock();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.limitDateTime = LocalDateTime.now().plus(1, timeUnit.toChronoUnit());
        this.requestCount = new AtomicInteger(0);
    }

    public String createDocument(Document document, String signature) throws IOException {
        if (LocalDateTime.now().isAfter(limitDateTime)) {
            requestCount.set(0);
            lock.lock();
            limitDateTime = LocalDateTime.now().plus(1, timeUnit.toChronoUnit());
            lock.unlock();
        } else {
            if (requestCount.get() >= requestLimit) {
                log.warn("request limit exceeded");
                return "";
            }
        }
        try {
            URL url = URI.create(API_URI).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
                    .create();
            String documentJson = gson.toJson(document);
            String payloadJson = "{\"product_document\":" + documentJson + ",\"signature\":\"" + signature + "}";
            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = payloadJson.getBytes("utf-8");
                outputStream.write(input, 0, input.length);
            } catch (IOException e) {
                log.warn("exception during body writing");
                connection.disconnect();
                throw new IOException("exception during body writing");
            }
            requestCount.incrementAndGet();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    connection.disconnect();
                    return response.toString();
                } catch (IOException e) {
                    log.warn("exception during response reading");
                    connection.disconnect();
                    throw new IOException("exception during response reading");
                }
            } else {
                log.warn("bad request, a response code " + connection.getResponseCode() + " was received");
                connection.disconnect();
                return "";
            }
        } catch (IOException e) {
            log.warn("exception during connecting");
            throw new IOException("exception during connecting");
        }
    }

    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private static final String doc_type = "LP_INTRODUCE_GOODS";
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private LocalDate production_date;
        private String production_type;
        private List<Product> products;
        private LocalDate reg_date;
        private String reg_number;

        public Document(Description description, String doc_id, String doc_status, boolean importRequest,
                        String owner_inn, String participant_inn, String producer_inn, LocalDate production_date,
                        String production_type, List<Product> products, LocalDate reg_date, String reg_number) {
            this.description = description;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }

        public static class Description {
            private String participantInn;

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }

        public static class Product {
            private String certificate_document;
            private LocalDate certificate_document_date;
            private String certificate_document_number;
            private String owner_inn;
            private String producer_inn;
            private LocalDate production_date;
            private String tnved_code;
            private String uit_code;
            private String uitu_code;

            public Product(String certificate_document, LocalDate certificate_document_date,
                           String certificate_document_number, String owner_inn, String producer_inn,
                           LocalDate production_date, String tnved_code, String uit_code, String uitu_code) {
                this.certificate_document = certificate_document;
                this.certificate_document_date = certificate_document_date;
                this.certificate_document_number = certificate_document_number;
                this.owner_inn = owner_inn;
                this.producer_inn = producer_inn;
                this.production_date = production_date;
                this.tnved_code = tnved_code;
                this.uit_code = uit_code;
                this.uitu_code = uitu_code;
            }
        }
    }

    public static class LocalDateSerializer implements JsonSerializer<LocalDate> {

        @Override
        public JsonElement serialize(LocalDate localDate, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
    }

}
