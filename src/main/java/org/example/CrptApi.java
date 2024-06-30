package org.example;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;


public class CrptApi {
    private static final int DEFAULT_REQUEST_LIMIT = 100;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final int POOL_SIZE = 1000;
    private static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final long msDelay;                                 //  delay in ms for each thread in pool
    private final ExecutorService executorService
            = Executors.newFixedThreadPool(POOL_SIZE);          //  thread pool
    private final Semaphore requestSemaphore;                   //  semaphore limiting the density of requests
    private ScheduledExecutorService scheduler;                 //  utility scheduler for generating test calls
    int count = 0;                                              //  utility counter for logger

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestSemaphore = new Semaphore(requestLimit);
        this.msDelay = roundUpDivision(timeUnit.toMillis(1), (Math.max(requestLimit / POOL_SIZE , 1 )));
    }

    public CrptApi() {          //  default constructor
        this(DEFAULT_TIME_UNIT, DEFAULT_REQUEST_LIMIT);
    }

    public static long roundUpDivision(long m, int n) {
        return (m + n - 1) / n;
    }

    @Builder
    public record ProductRegistration(CrptApi.ProductRegistration.Description description,
                                      @JsonProperty("doc_id") String docId,
                                      @JsonProperty("doc_status") String docStatus,
                                      @JsonProperty("doc_type") String docType,
                                      boolean importRequest,
                                      @JsonProperty("owner_inn") String ownerInn,
                                      @JsonProperty("participant_inn") String participantInn,
                                      @JsonProperty("producer_inn") String producerInn,
                                      @JsonProperty("production_date")
                                          @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone = "UTC")
                                          Date productionDate,
                                      @JsonProperty("production_type") String productionType,
                                      List<Product> products,
                                      @JsonProperty("reg_date")
                                          @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone = "UTC")
                                          Date regDate,
                                      @JsonProperty("reg_number") String regNumber) {
        private record Description(String participantInn) {
        }
        }

    @Data
    @Builder
    public static class Product {
        @JsonProperty("certificate_document")
        private final String certificateDocument;
        @JsonProperty("certificate_document_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone = "UTC")
        private final Date certificateDocumentDate;
        @JsonProperty("certificate_document_number")
        private final String certificateDocumentNumber;
        @JsonProperty("owner_inn")
        private final String ownerInn;
        @JsonProperty("producer_inn")
        private final String producerInn;
        @JsonProperty("production_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_PATTERN, timezone = "UTC")
        private final Date productionDate;
        @JsonProperty("tnved_code")
        private final String tnvedCode;
        @JsonProperty("uit_code")
        private final String uitCode;
        @JsonProperty("uitu_code")
        private final String uituCode;
    }


    private static String convertToJson(ProductRegistration registration) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(registration);
    }

    private void sendPostRequest(ProductRegistration registration, String signature) {
        if (requestSemaphore.tryAcquire()) {
            executorService.submit(() -> {
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    String json = convertToJson(registration);
                    HttpPost httpPost = new HttpPost(API_URL);
                    httpPost.setHeader("Content-Type", "application/json");
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("X-Signature", signature);
                    StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
                    httpPost.setEntity(entity);
                    handleResponse(httpClient.execute(httpPost));
                } catch (IOException e) {
                    logger.error("Error closing HttpClient: {}", e.getMessage(), e);
                } finally {
                    requestSemaphore.release();
                    delayAfterRequest();
                }
            });
        } else {
            count++;
            logger.info("Request limit exceeded. Cannot send request at this time {}", count);
        }
    }

    private void delayAfterRequest() {
        try {
            Thread.sleep(msDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while waiting: {}", e.getMessage(), e);
        }
    }

    private static void handleResponse(CloseableHttpResponse response) throws IOException {
        try (response) {
            logger.info("Response status: {}", response.getStatusLine());
            HttpEntity responseEntity = response.getEntity();
            String responseBody = EntityUtils.toString(responseEntity);
            logger.info("Response body: {}", responseBody);
        }
    }

    //  BELOW THE LOGIC FOR TESTING THE APPLICATION IN MAIN METHOD

    public static ProductRegistration createProductRegistration(String participantInn, String docId, String docStatus, String docType,
                                                                boolean importRequest, String ownerInn, String producerInn, Date productionDate,
                                                                String productionType, List<Product> products, Date regDate, String regNumber) {
        return ProductRegistration.builder()
                .description(new ProductRegistration.Description(participantInn))
                .docId(docId)
                .docStatus(docStatus)
                .docType(docType)
                .importRequest(importRequest)
                .ownerInn(ownerInn)
                .producerInn(producerInn)
                .participantInn(participantInn)
                .productionDate(productionDate)
                .productionType(productionType)
                .products(products)
                .regDate(regDate)
                .regNumber(regNumber)
                .build();
    }

    static Date createDate(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        int calendarMonth = month-1; // in calendar JANUARY has number 0
        calendar.set(year, calendarMonth, dayOfMonth);
        return calendar.getTime();
    }

    public void sendRandomRequests(int maxRequestsPerSecond, ProductRegistration productRegistration, String signature) {
        //  this test method makes random quantity of tries to requests with productRegistration and signature
        //  through sendPostRequest() method each second
        scheduler = Executors.newScheduledThreadPool(1);
        Random random = new Random();

        scheduler.scheduleAtFixedRate(() -> {
            int requestsToSend = random.nextInt(maxRequestsPerSecond + 1);
            System.out.println(requestsToSend);
            for (int i = 0; i < requestsToSend; i++) {
                try {
                    sendPostRequest(productRegistration, signature);
                } catch (Exception e) {
                    logger.error("Thread interrupted while waiting: {}", e.getMessage(), e);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void shutdownScheduler() {               //  shutting down utility scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
