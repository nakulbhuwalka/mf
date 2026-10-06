package com.mf.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HealthSteps {

    private final HttpClient http = HttpClient.newHttpClient();
    private HttpResponse<String> response;

    @AfterAll
    public static void stopServices() {
        ServiceProcess.stopAll();
    }

    @Given("the {string} service is running")
    public void theServiceIsRunning(String service) throws Exception {
        ServiceProcess.named(service).ensureRunning();
    }

    @When("I GET {string} from {string}")
    public void iGetFrom(String path, String service) throws Exception {
        var request = HttpRequest.newBuilder(ServiceProcess.named(service).uri(path)).GET().build();
        response = http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int expected) {
        assertEquals(expected, response.statusCode(), response.body());
    }
}
