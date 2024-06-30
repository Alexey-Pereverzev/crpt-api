package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.example.CrptApi.createDate;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(CrptApi.class);

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 50);
        List<CrptApi.Product> products = defineSampleProducts();
        CrptApi.ProductRegistration productRegistration = CrptApi.createProductRegistration("string",
                "string", "string", "LP_INTRODUCE_GOODS", true, "string",
                "string", createDate(2020,1,23), "string", products,
                createDate(2020,1,23),"string");

        crptApi.sendRandomRequests(100, productRegistration, "exampleSignature");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted while waiting: {}", e.getMessage(), e);
        }

        crptApi.shutdownScheduler();
    }

    static List<CrptApi.Product> defineSampleProducts() {
        CrptApi.Product product = CrptApi.Product.builder()
                .certificateDocument("string")
                .certificateDocumentDate(createDate(2020, 1, 23))
                .certificateDocumentNumber("string")
                .ownerInn("string")
                .producerInn("string")
                .productionDate(createDate(2020,1,23))
                .tnvedCode("string")
                .uitCode("string")
                .uituCode("string")
                .build();
        return List.of(product);
    }

}
