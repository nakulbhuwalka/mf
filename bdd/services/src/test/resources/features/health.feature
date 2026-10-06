Feature: Service health

  Scenario: fund-data reports healthy
    Given the "fund-data" service is running
    When I GET "/actuator/health" from "fund-data"
    Then the response status is 200

  Scenario: statement-parser reports healthy
    Given the "statement-parser" service is running
    When I GET "/health" from "statement-parser"
    Then the response status is 200
