package nl.wjglerum._02_reactive;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import org.hamcrest.MatcherAssert;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.in;

@QuarkusTest
@TestHTTPEndpoint(ReactiveShowdownResource.class)
class ReactiveShowdownResourceTest {

    @Test
    void testReactiveFloodEndpoint() {
        given()
                .queryParam("count", 5)
                .when()
                .get("/flood")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("requested", equalTo(5))
                .body("succeeded", equalTo(5))
                .body("failed", equalTo(0));
    }

    @Test
    void testReactiveFailFastEndpoint() {
        var status = given()
                .when()
                .get("/failfast")
                .then()
                .statusCode(in(List.of(200, 503)))
                .extract().statusCode();
        MatcherAssert.assertThat(status, in(List.of(200, 503)));
    }
}
