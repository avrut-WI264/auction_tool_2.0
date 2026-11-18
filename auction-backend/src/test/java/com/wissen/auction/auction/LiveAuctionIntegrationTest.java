package com.wissen.auction.auction;

import tools.jackson.databind.*;
import com.wissen.auction.auth.*;
import com.wissen.auction.common.DataInitializer;
import com.wissen.auction.player.*;
import com.wissen.auction.team.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real PostgreSQL tests. All tables live in a disposable, isolated schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.show-sql=false", "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.jwt.secret=test-only-secret-for-owner-auction-integration-012345678901234567890",
                "spring.flyway.enabled=false", "app.cookie.secure=false", "app.allowed.origins=http://localhost:5173"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = LiveAuctionIntegrationTest.SchemaCleanup.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class LiveAuctionIntegrationTest {
    static final String SCHEMA = "auction_test_" + UUID.randomUUID().toString().replace("-", "");
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ConfigurableApplicationContext applicationContext;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired PlayerRepository players;
    @Autowired TeamRepository teams;
    @Autowired BidLogRepository bids;
    @Autowired LiveAuctionRepository sessions;
    @Autowired LiveAuctionService live;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthSessionRepository authSessions;
    @Autowired com.wissen.auction.auction.realtime.AuctionEventRepository eventLog;
    @Autowired com.wissen.auction.auction.realtime.AuctionEventDispatcher dispatcher;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @LocalServerPort int port;
    @MockitoBean DataInitializer initializer;
    @MockitoBean TeamPurseSync purseSync;
    Cookie admin, owner, otherOwner;
    Cookie csrfCookie;
    String csrfValue;
    Team first, second;

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("TEST_POSTGRES_URL"),
                System.getenv("TEST_POSTGRES_USER"), System.getenv("TEST_POSTGRES_PASSWORD"));
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        String base = System.getenv("TEST_POSTGRES_URL");
        properties.add("spring.datasource.url", () -> base + (base.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> System.getenv("TEST_POSTGRES_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TEST_POSTGRES_PASSWORD"));
    }

    static void cleanup() throws SQLException {
        // SCHEMA is generated above, never user-supplied; public tables are untouched.
        if (!SCHEMA.matches("auction_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema.");
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    public static class SchemaCleanup extends org.springframework.test.context.support.AbstractTestExecutionListener {
        @Override public int getOrder() { return 2000; }
        @Override public void afterTestClass(org.springframework.test.context.TestContext context) throws SQLException {
            // Runs after DirtiesContext has stopped sockets, schedulers, and JPA.
            cleanup();
        }
    }

    @BeforeEach
    void setup() throws Exception {
        synchronized (dispatcher) {
            eventLog.deleteAll();
            ((Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(dispatcher, "cursors")).clear();
        }
        authSessions.deleteAll();
        jdbc.update("delete from login_attempts");
        sessions.deleteAll();
        bids.deleteAll();
        users.deleteAll();
        players.deleteAll();
        teams.deleteAll();
        first = teams.save(Team.builder().teamName("First").ownerName("Owner A").location("Pune").build());
        second = teams.save(Team.builder().teamName("Second").ownerName("Owner B").location("Pune").build());
        for (int i = 0; i < 2; i++) players.save(Player.builder().wissenId("TEST" + i).fullName("Player " + i)
                .email("player" + i + "@example.com").gender(Player.Gender.Female).location("Pune")
                .skillLevel(Player.SkillLevel.Beginner).basePrice(1000).build());
        users.save(AppUser.builder().username("admin").password(encoder.encode("test-password"))
                .role("admin").city("Pune").build());
        MvcResult csrfResponse = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        csrfValue = json.readTree(csrfResponse.getResponse().getContentAsString()).get("token").asText();
        String securityCookie = csrfResponse.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        csrfCookie = new Cookie("XSRF-TOKEN", securityCookie.split(";", 2)[0].split("=", 2)[1]);
        admin = login("admin");
        createOwner("owner", first.getId());
        createOwner("other", second.getId());
        owner = login("owner");
        otherOwner = login("other");
        live.start("Pune", "admin", "All", "All", 0L);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return request -> {
            List<Cookie> cookies = new ArrayList<>();
            if (request.getCookies() != null) cookies.addAll(Arrays.asList(request.getCookies()));
            cookies.add(csrfCookie);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader("X-XSRF-TOKEN", csrfValue);
            return request;
        };
    }

    Cookie login(String username) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
                .andExpect(status().isOk()).andReturn();
        String cookie = response.getResponse().getHeader("Set-Cookie");
        return new Cookie("wbpl_jwt", cookie.split(";", 2)[0].split("=", 2)[1]);
    }

    void createOwner(String name, Long teamId) throws Exception {
        mvc.perform(post("/api/pune/owners").with(csrf()).cookie(admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", name, "password", "test-password", "teamId", teamId))))
                .andExpect(status().isOk());
    }

    String bidBody(LiveAuctionSnapshot state, Long teamId) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("playerId", state.activePlayer().getId());
        input.put("revision", state.revision());
        input.put("increment", 500);
        if (teamId != null) input.put("teamId", teamId);
        return json.writeValueAsString(input);
    }

    private JsonNode pollRevision(BlockingQueue<JsonNode> messages, long revision) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && message.path("revision").asLong() >= revision) return message;
        }
        return null;
    }

    @Test
    void checkedUploadFailuresDoNotClearPausedBidsOrCommitEvents() throws Exception {
        LiveAuctionSnapshot paused = live.pause("pune", "admin", live.getState("pune").revision());
        long eventsBefore = eventLog.count();
        var file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        org.mockito.Mockito.when(file.getInputStream()).thenThrow(new java.io.IOException("simulated upload failure"));
        assertThatThrownBy(() -> applicationContext.getBean(PlayerService.class).importFromExcel(file, "pune"))
                .isInstanceOf(java.io.IOException.class);
        assertThat(live.getState("pune").revision()).isEqualTo(paused.revision());
        assertThat(live.getState("pune").activePlayer().getId()).isEqualTo(paused.activePlayer().getId());
        assertThat(eventLog.count()).isEqualTo(eventsBefore);
    }

    @Test
    void committedEventsRetryAfterPublisherFailureAndRollbackWithTheAuction() throws Exception {
        var output = org.mockito.Mockito.mock(org.springframework.messaging.simp.SimpMessagingTemplate.class);
        var metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try {
            var reader = new com.wissen.auction.auction.realtime.AuctionEventDispatcher(eventLog, sessions, output, json, metrics);
            reader.initialize();
            org.mockito.Mockito.doThrow(new org.springframework.messaging.MessagingException("simulated failure"))
                    .doNothing().when(output).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/auction/pune"),
                            org.mockito.ArgumentMatchers.any(com.wissen.auction.auction.realtime.AuctionNotice.class));
            LiveAuctionSnapshot before = live.getState("pune");
            mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(before, null)))
                    .andExpect(status().isOk());
            reader.dispatch();
            assertThat(eventLog.count()).isEqualTo(2);
            reader.dispatch();
            org.mockito.Mockito.verify(output, org.mockito.Mockito.times(2)).convertAndSend(
                    org.mockito.ArgumentMatchers.eq("/topic/auction/pune"),
                    org.mockito.ArgumentMatchers.any(com.wissen.auction.auction.realtime.AuctionNotice.class));
            long revision = live.getState("pune").revision();
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                    applicationContext.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                var state = applicationContext.getBean(com.wissen.auction.auction.application.AuctionLocks.class).acquire("pune");
                state.incrementRevision();
                applicationContext.getBean(com.wissen.auction.auction.realtime.AuctionEventLog.class)
                        .append(state, com.wissen.auction.auction.domain.AuctionEventType.STATE);
                status.setRollbackOnly();
            });
            assertThat(eventLog.count()).isEqualTo(2);
            assertThat(live.getState("pune").revision()).isEqualTo(revision);
        } finally { metrics.close(); }
    }

    @Test
    void passwordChangesRevokeEverySessionAndMetricsRemainPrivate() throws Exception {
        Cookie anotherSession = login("owner");
        mvc.perform(post("/api/auth/password").with(csrf()).cookie(owner).contentType("application/json")
                .content(json.writeValueAsString(Map.of("currentPassword", "test-password", "newPassword", "replacement-password"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(anotherSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
    }

    @Test
    void csrfProtectionAndSessionRevocationAreEnforced() throws Exception {
        String input = json.writeValueAsString(Map.of("username", "owner", "password", "test-password"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(input))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var browser = java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
        var tokenResponse = browser.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/auth/csrf")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        JsonNode csrfBody = json.readTree(tokenResponse.body());
        assertThat(tokenResponse.headers().allValues("Set-Cookie").stream().anyMatch(value -> value.startsWith("XSRF-TOKEN="))).isTrue();
        var loginResponse = browser.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json").header(csrfBody.get("headerName").asText(), csrfBody.get("token").asText())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input)).build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + owner.getValue())).andExpect(status().isUnauthorized());
    }

    @Test
    void publicViewsNeverReturnContactsEmployeeIdsOrInlinePhotos() throws Exception {
        Player player = players.findAll().get(0);
        player.setMobileNumber("private-phone");
        player.setImageUrl("data:image/png;base64,private-image");
        players.save(player);
        for (String endpoint : List.of("/api/pune/players", "/api/pune/players/" + player.getId(), "/api/pune/auction/state")) {
            String body = mvc.perform(get(endpoint)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("private-phone", "player0@example.com", "private-image", "TEST0");
        }
        mvc.perform(get("/api/pune/players/" + player.getId() + "/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pune/players/" + player.getId() + "/private").cookie(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pune/players/" + player.getId() + "/private").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mobileNumber").value("private-phone"));
    }

    @Test
    void databaseBackedLoginLimitsAndAccountChangesApplyImmediately() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", "blocked-user", "password", "invalid"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "blocked-user", "password", "invalid"))))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        AppUser user = users.findByUsername("owner").orElseThrow();
        user.setEnabled(false);
        users.save(user);
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(status().isUnauthorized());
    }

    @Test
    void resetsAndCatalogWritesRequireCurrentRevisionAndAPausedAuction() throws Exception {
        LiveAuctionSnapshot before = live.getState("pune");
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(before, null)))
                .andExpect(status().isOk());
        LiveAuctionSnapshot bid = live.getState("pune");
        assertThatThrownBy(() -> live.start("pune", "admin", "All", "All", bid.revision())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> live.pause("pune", "admin", before.revision())).isInstanceOf(IllegalStateException.class);
        String teamEdit = json.writeValueAsString(Map.of("teamName", "Renamed", "ownerName", "Owner A"));
        mvc.perform(put("/api/pune/teams/" + first.getId()).with(csrf()).cookie(admin).contentType("application/json").content(teamEdit))
                .andExpect(status().isConflict());
        LiveAuctionSnapshot paused = live.pause("pune", "admin", bid.revision());
        assertThat(paused.currentBid()).isEqualTo(1500);
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(otherOwner).contentType("application/json").content(bidBody(paused, null)))
                .andExpect(status().isConflict());
        String oldVersion = mvc.perform(get("/api/pune/data-version")).andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/pune/teams/" + first.getId()).with(csrf()).cookie(admin).contentType("application/json").content(teamEdit))
                .andExpect(status().isOk());
        String newVersion = mvc.perform(get("/api/pune/data-version")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(newVersion).get("hash")).isNotEqualTo(json.readTree(oldVersion).get("hash"));
        assertThat(live.getState("pune").activePlayer()).isNull();
    }

    @Test
    void anotherBackendInstanceReceivesTheSameCommittedBid() throws Exception {
        var environment = new org.springframework.core.env.StandardEnvironment();
        Map<String, Object> properties = new HashMap<>();
        String base = System.getenv("TEST_POSTGRES_URL");
        properties.put("spring.datasource.url", base + (base.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        properties.put("spring.datasource.username", System.getenv("TEST_POSTGRES_USER"));
        properties.put("spring.datasource.password", System.getenv("TEST_POSTGRES_PASSWORD"));
        properties.put("spring.flyway.enabled", "false");
        properties.put("spring.jpa.hibernate.ddl-auto", "validate");
        properties.put("spring.jpa.show-sql", "false");
        properties.put("app.jwt.secret", "test-only-secret-for-owner-auction-integration-012345678901234567890");
        properties.put("app.cookie.secure", "false");
        properties.put("server.port", "0");
        properties.put("logging.level.root", "ERROR");
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("second-node", properties));
        try (var node = new org.springframework.boot.builder.SpringApplicationBuilder(com.wissen.auction.AuctionBackendApplication.class)
                .environment(environment).run()) {
            int secondPort = ((org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext) node).getWebServer().getPort();
            WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
            client.setMessageConverter(new JacksonJsonMessageConverter());
            StompSession socket = null;
            try {
                socket = client.connectAsync("ws://localhost:" + secondPort + "/ws-auction", new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
                BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
                socket.subscribe("/topic/auction/pune", new StompFrameHandler() {
                    public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
                    public void handleFrame(StompHeaders headers, Object payload) { messages.add((JsonNode) payload); }
                });
                assertThat(messages.poll(5, TimeUnit.SECONDS)).isNotNull();
                LiveAuctionSnapshot before = live.getState("pune");
                mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(before, null)))
                        .andExpect(status().isOk());
                JsonNode received = messages.poll(5, TimeUnit.SECONDS);
                assertThat(received).isNotNull();
                assertThat(received.path("currentBid").asInt()).isEqualTo(1500);
                assertThat(received.path("revision").asLong()).isEqualTo(before.revision() + 1);
                assertThat(received.has("auctionQueue")).isFalse();
                assertThat(eventLog.count()).isEqualTo(2);
            } finally {
                if (socket != null && socket.isConnected()) socket.disconnect();
                client.stop();
            }
        }
    }

    @Test
    void sustainedGuestReadsAndAFiftyBidBurstRemainConsistent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Long> timings = Collections.synchronizedList(new ArrayList<>());
        long began = System.nanoTime();
        try {
            var client = java.net.http.HttpClient.newBuilder().executor(executor).build();
            List<java.util.concurrent.CompletableFuture<Void>> reads = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                long start = System.nanoTime();
                reads.add(client.sendAsync(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + "/api/pune/auction/state")).build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.body()).doesNotContain("player0@example.com");
                    timings.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                }));
            }
            CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
            LiveAuctionSnapshot state = live.getState("pune");
            CountDownLatch startBids = new CountDownLatch(1);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String bidder = i % 2 == 0 ? "owner" : "other";
                attempts.add(executor.submit(() -> {
                    startBids.await();
                    BidRequest request = new BidRequest();
                    request.setPlayerId(state.activePlayer().getId()); request.setRevision(state.revision()); request.setIncrement(500);
                    try { live.bid("pune", bidder, request); return true; }
                    catch (IllegalStateException conflict) { return false; }
                }));
            }
            startBids.countDown();
            int accepted = 0;
            for (Future<Boolean> attempt : attempts) if (attempt.get(30, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
            assertThat(bids.count()).isEqualTo(1);
            timings.sort(Long::compareTo);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/load-smoke.json"), json.writeValueAsString(Map.of(
                    "guestReads", 100, "guestReadErrors", 0, "guestReadP95Ms", timings.get(94),
                    "bidBurst", 50, "bidsAccepted", accepted, "staleBidsRejected", 50 - accepted,
                    "totalDurationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began))));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void authenticationUsesTypedPublicProfilesAndRejectsInvalidCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "owner", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", "owner", "password", "test-password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.teamId").value(first.getId()));
        assertThat(users.findByUsername("owner").orElseThrow().getPassword()).isNotEqualTo("test-password");
    }

    @Test
    void explicitAdminRoutesShareTheSameCommittedAuctionState() throws Exception {
        LiveAuctionSnapshot state = live.getState("pune");
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(state, null)))
                .andExpect(status().isOk());
        state = live.getState("pune");
        mvc.perform(post("/api/pune/auction/revert").with(csrf()).cookie(admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("playerId", state.activePlayer().getId(), "revision", state.revision()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentBid").value(1000));
        state = live.getState("pune");
        Long firstPlayer = state.activePlayer().getId();
        mvc.perform(post("/api/pune/auction/pass").with(csrf()).cookie(admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("playerId", firstPlayer, "revision", state.revision()))))
                .andExpect(status().isOk());
        assertThat(live.getState("pune").activePlayer().getId()).isNotEqualTo(firstPlayer);
        live.pause("pune", "admin", live.getState("pune").revision());
        mvc.perform(post("/api/pune/auction/re-auction").with(csrf()).cookie(admin)
                .param("skillLevel", "Beginner").param("gender", "Female").contentType("application/json")
                .content(json.writeValueAsString(Map.of("revision", live.getState("pune").revision()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activePlayer.id").value(firstPlayer));
        state = live.getState("pune");
        mvc.perform(post("/api/pune/auction/next").with(csrf()).cookie(admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("playerId", firstPlayer, "revision", state.revision()))))
                .andExpect(status().isOk());
        assertThat(live.getState("pune").activePlayer().getId()).isNotEqualTo(firstPlayer);
    }

    @Test
    void ownerIdentityAndReadOnlyGuestPermissions() throws Exception {
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("owner")).andExpect(jsonPath("$.teamId").value(first.getId()));
        mvc.perform(get("/api/pune/auction/state")).andExpect(status().isOk());
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).contentType("application/json")
                .content(bidBody(live.getState("pune"), first.getId()))).andExpect(status().isUnauthorized());
        for (String action : List.of("start", "sell", "pass", "next", "revert", "re-auction")) {
            mvc.perform(post("/api/pune/auction/" + action).with(csrf()).cookie(owner).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/pune/owners").with(csrf()).cookie(owner).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pune/teams").with(csrf()).cookie(owner).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bidsAreBoundToOwnerTeamCityAndCurrentRevision() throws Exception {
        LiveAuctionSnapshot state = live.getState("pune");
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json")
                .content(bidBody(state, second.getId()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/mumbai/auction/bid").with(csrf()).cookie(owner).contentType("application/json")
                .content(bidBody(state, null))).andExpect(status().isForbidden());
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json")
                .content(bidBody(state, null))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBid").value(1500)).andExpect(jsonPath("$.highestBidderTeamId").value(first.getId()));
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(otherOwner).contentType("application/json")
                .content(bidBody(state, null))).andExpect(status().isConflict());
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json")
                .content(bidBody(live.getState("pune"), null))).andExpect(status().isConflict());
        assertThat(live.getState("pune").currentBid()).isEqualTo(1500);
        assertThat(bids.count()).isEqualTo(1);
    }

    @Test
    void iteratorBasedRosterRemovalPersistsReleaseAndDeletionWithoutLosingOtherPlayers() throws Exception {
        live.pause("pune", "admin", live.getState("pune").revision());
        List<Long> rosterIds = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Player sold = players.save(Player.builder().wissenId("SOLD" + i).fullName("Sold " + i)
                    .email("sold" + i + "@example.com").gender(Player.Gender.Female).location("Pune")
                    .skillLevel(Player.SkillLevel.Beginner).basePrice(1000).status(Player.PlayerStatus.SOLD)
                    .soldTeam(first).soldPrice(i * 1000).build());
            rosterIds.add(sold.getId());
        }
        mvc.perform(post("/api/pune/teams/" + first.getId() + "/release-player").with(csrf()).cookie(admin)
                .contentType("application/json").content(json.writeValueAsString(Map.of("playerId", rosterIds.get(1)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPlayers").value(2))
                .andExpect(jsonPath("$.purseRemaining").value(96000));
        Player released = players.findById(rosterIds.get(1)).orElseThrow();
        assertThat(released.getSoldTeam()).isNull();
        assertThat(released.getStatus()).isEqualTo(Player.PlayerStatus.UNSOLD);

        mvc.perform(delete("/api/pune/players/" + rosterIds.get(0)).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        assertThat(players.existsById(rosterIds.get(0))).isFalse();
        mvc.perform(get("/api/pune/teams/" + first.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPlayers").value(1)).andExpect(jsonPath("$.purseRemaining").value(97000))
                .andExpect(jsonPath("$.players[0].id").value(rosterIds.get(2)));
    }

    @Test
    void invalidIncrementsInsufficientPurseAndFullRostersCannotBid() throws Exception {
        LiveAuctionSnapshot state = live.getState("pune");
        String invalidIncrement = bidBody(state, null).replace("500", "750");
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(invalidIncrement))
                .andExpect(status().isBadRequest());
        Player expensive = players.save(Player.builder().wissenId("EXPENSIVE").fullName("Previously sold")
                .email("sold@example.com").gender(Player.Gender.Female).location("Pune")
                .skillLevel(Player.SkillLevel.Beginner).basePrice(1000).status(Player.PlayerStatus.SOLD)
                .soldTeam(first).soldPrice(99500).build());
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(state, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Insufficient team purse."));
        players.deleteById(expensive.getId());
        for (int i = 0; i < 10; i++) players.save(Player.builder().wissenId("ROSTER" + i).fullName("Roster " + i)
                .email("roster" + i + "@example.com").gender(Player.Gender.Female).location("Pune")
                .skillLevel(Player.SkillLevel.Beginner).basePrice(1000).status(Player.PlayerStatus.SOLD)
                .soldTeam(first).soldPrice(0).build());
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(state, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Team roster is full."));
        assertThat(live.getState("pune").revision()).isEqualTo(state.revision());
        assertThat(bids.count()).isZero();
    }

    @Test
    void simultaneousBidsAcceptOnlyOneAtTheDisplayedPrice() throws Exception {
        LiveAuctionSnapshot state = live.getState("pune");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String name : List.of("owner", "other")) results.add(executor.submit(() -> {
                go.await();
                BidRequest request = new BidRequest();
                request.setPlayerId(state.activePlayer().getId());
                request.setRevision(state.revision());
                request.setIncrement(500);
                try { live.bid("pune", name, request); return true; }
                catch (IllegalStateException expected) { return false; }
            }));
            go.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
            assertThat(live.getState("pune").currentBid()).isEqualTo(1500);
            assertThat(bids.count()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void saleUndoAndNewPlayerUseCommittedState() throws Exception {
        LiveAuctionSnapshot start = live.getState("pune");
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(start, null)))
                .andExpect(status().isOk());
        LiveAuctionSnapshot bid = live.getState("pune");
        assertThatThrownBy(() -> live.sell("pune", "admin", bid.activePlayer().getId(), bid.revision(), second.getId(), 1500))
                .isInstanceOf(IllegalStateException.class);
        LiveAuctionSnapshot reverted = live.revert("pune", "admin", bid.activePlayer().getId(), bid.revision());
        assertThat(reverted.currentBid()).isEqualTo(1000);
        assertThat(reverted.highestBidderTeamId()).isNull();
        mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(otherOwner).contentType("application/json").content(bidBody(reverted, null)))
                .andExpect(status().isOk());
        LiveAuctionSnapshot winning = live.getState("pune");
        LiveAuctionSnapshot next = live.sell("pune", "admin", winning.activePlayer().getId(), winning.revision(), second.getId(), 1500);
        assertThat(next.activePlayer().getId()).isNotEqualTo(winning.activePlayer().getId());
        assertThat(next.currentBid()).isEqualTo(1000);
        assertThat(next.highestBidderTeamId()).isNull();
        assertThat(next.cityTeams().stream().filter(t -> t.getId().equals(second.getId())).findFirst().orElseThrow().getPurseRemaining()).isEqualTo(98500);
        assertThatThrownBy(() -> live.sell("pune", "admin", winning.activePlayer().getId(), winning.revision(), second.getId(), 1500))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void committedBidReachesPublicWebSocketAndReconnectSnapshot() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.initialize();
        client.setTaskScheduler(scheduler);
        StompSession socket = null;
        try {
            socket = client.connectAsync("ws://localhost:" + port + "/ws-auction", new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
            BlockingQueue<JsonNode> updates = new LinkedBlockingQueue<>();
            socket.subscribe("/topic/auction/pune", new StompFrameHandler() {
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
                public void handleFrame(StompHeaders headers, Object payload) { updates.add((JsonNode) payload); }
            });
            JsonNode connectedSnapshot = updates.poll(5, TimeUnit.SECONDS);
            assertThat(connectedSnapshot).isNotNull();
            assertThat(connectedSnapshot.path("eventType").asText()).isEqualTo("SNAPSHOT");
            LiveAuctionSnapshot initial = live.getState("pune");
            mvc.perform(post("/api/pune/auction/bid").with(csrf()).cookie(owner).contentType("application/json").content(bidBody(initial, null)))
                    .andExpect(status().isOk());
            JsonNode update = pollRevision(updates, initial.revision() + 1);
            assertThat(update).isNotNull();
            assertThat(update.path("currentBid").asInt()).isEqualTo(1500);
            assertThat(update.path("highestBidderTeamId").asLong()).isEqualTo(first.getId());
            mvc.perform(get("/api/pune/auction/state")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.revision").value(update.path("revision").asLong())).andExpect(jsonPath("$.currentBid").value(1500));
            socket.disconnect();
            socket = client.connectAsync("ws://localhost:" + port + "/ws-auction", new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
            socket.subscribe("/topic/auction/pune", new StompFrameHandler() {
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
                public void handleFrame(StompHeaders headers, Object payload) { updates.add((JsonNode) payload); }
            });
            JsonNode reconnected = updates.poll(5, TimeUnit.SECONDS);
            assertThat(reconnected).isNotNull();
            assertThat(reconnected.path("revision").asLong()).isEqualTo(update.path("revision").asLong());
            assertThat(reconnected.path("currentBid").asInt()).isEqualTo(1500);
        } finally {
            if (socket != null && socket.isConnected()) socket.disconnect();
            client.stop();
            scheduler.shutdown();
        }
    }
}
