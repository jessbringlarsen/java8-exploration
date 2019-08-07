package dk.bringlarsen.exploration.java.http;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.Assert.assertThat;

public class HttpClientExplorationTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.options()
            .dynamicPort()
            .extensions(new ResponseTemplateTransformer(true)));

    @Before
    public void setup() {
        WireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/persons/.*"))
        .willReturn(WireMock.aResponse()
            .withBody("id: " + "{{request.requestLine.pathSegments.[1]}}")
            .withTransformers("response-template")));
    }

    @Test
    public void simpleGet() throws URISyntaxException, IOException, InterruptedException {
        String url = wireMockRule.url("/persons/1");
        HttpResponse<String> response = HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder(new URI(url)).GET().build(), 
            HttpResponse.BodyHandlers.ofString());
    
        assertThat(response.body(), CoreMatchers.is("id: 1"));
    }

    @Test
    public void asyncGet() throws URISyntaxException {
        List<URI> targets = Arrays.asList(
                new URI(wireMockRule.url("/persons/1")),
                new URI(wireMockRule.url("/persons/2")));

        HttpClient client = HttpClient.newHttpClient();
        LinkedList<CompletableFuture<String>> responses = targets.stream()
          .map(target -> client
            .sendAsync(
              HttpRequest.newBuilder(target).GET().build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.body()))
          .collect(Collectors.toCollection(LinkedList::new));

        List<String> results = responses.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        assertThat(results, CoreMatchers.hasItems("id: 1", "id: 2"));
    }
}