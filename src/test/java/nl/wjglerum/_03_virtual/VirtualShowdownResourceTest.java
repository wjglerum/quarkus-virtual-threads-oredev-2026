package nl.wjglerum._03_virtual;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.virtual.ShouldNotPin;
import io.quarkus.test.junit.virtual.VirtualThreadUnit;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import org.hamcrest.MatcherAssert;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.in;

@QuarkusTest
@ShouldNotPin
@VirtualThreadUnit
@TestHTTPEndpoint(VirtualShowdownResource.class)
class VirtualShowdownResourceTest {

    @Test
    void testVirtualRaceEndpoint() {
        given()
                .when()
                .get("/race")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body(containsString("Virtual Coffee"));
    }

    @Test
    void testVirtualFloodEndpoint() {
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
    void testVirtualFailFastEndpoint() {
        var status = given()
                .when()
                .get("/failfast")
                .then()
                .statusCode(in(List.of(200, 503)))
                .extract().statusCode();
        MatcherAssert.assertThat(status, in(List.of(200, 503)));
    }
}
