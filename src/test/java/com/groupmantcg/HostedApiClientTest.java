package com.groupmantcg;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HostedApiClientTest
{
	private TestHttpServer server;
	private HostedApiClient api;

	@Before
	public void setUp() throws Exception
	{
		server = new TestHttpServer();
		api = new HostedApiClient(new OkHttpClient(), new Gson(), server.baseUrl());
	}

	@After
	public void tearDown()
	{
		server.close();
	}

	@Test
	public void createsGroupWithSetupHeaderAndExplicitPrivateServerIdentity() throws Exception
	{
		server.enqueue(201, "{\"group\":{\"id\":\"group-1\"},"
			+ "\"member\":{\"id\":\"member-1\",\"label\":\"Owner\",\"role\":\"owner\","
			+ "\"status\":\"approved\",\"token\":\"secret-token\"},"
			+ "\"invite\":{\"code\":\"ABCD-EFGH-JK23\",\"expiresAt\":1234}}");

		String setupKey = "example-private-setup-key-1234";
		HostedApiClient.CreateResponse response = api.createGroup(setupKey, "Sqwiglyy", "shared");
		assertEquals("group-1", response.group.id);
		assertEquals("secret-token", response.member.token);

		CapturedRequest request = server.takeRequest();
		assertEquals("/v1/groups", request.path);
		assertEquals("POST", request.method);
		assertNull(request.header("Authorization"));
		assertEquals(setupKey, request.header("X-Groupman-Setup-Key"));
		assertTrue(request.body.contains("\"playerName\":\"Sqwiglyy\""));
		assertTrue(request.body.contains("\"collectionMode\":\"shared\""));
		assertFalse(request.body.contains(setupKey));
		assertFalse(request.body.contains("groupName"));
	}

	@Test
	public void authenticatesSyncAndParsesMissedPacks() throws Exception
	{
		server.enqueue(200, "{\"nextCursor\":9,\"hasMore\":false,\"events\":[{"
			+ "\"sequence\":9,\"eventId\":\"pack_9\",\"openedAt\":10000,\"receivedAt\":12000,"
			+ "\"member\":{\"id\":\"other\",\"label\":\"Member 123456\",\"playerName\":\"Friend\"},"
			+ "\"cards\":[{\"name\":\"Great Olm\",\"foil\":true,\"isNew\":true}]}],"
			+ "\"collection\":{\"version\":4,\"changed\":true,\"unlocks\":[\"Great Olm\"]}}");

		HostedApiClient.SyncResponse response = api.sync(profile(), 7L, 4L, 3L);
		assertEquals(9L, response.nextCursor);
		assertFalse(response.hasMore);
		assertEquals("Member 123456", response.events.get(0).member.label);
		assertEquals("Friend", response.events.get(0).member.playerName);
		assertEquals(12_000L, response.events.get(0).receivedAt);
		assertEquals("Great Olm", response.events.get(0).cards.get(0).name);

		CapturedRequest request = server.takeRequest();
		assertEquals("Bearer member-token", request.header("Authorization"));
		assertTrue(request.path.startsWith("/v1/groups/group-1/sync?"));
		assertTrue(request.path.contains("after=7"));
		assertTrue(request.path.contains("topTrumpsAfter=4"));
		assertTrue(request.path.contains("collectionVersion=3"));
	}

	@Test
	public void joinsWithTheApprovedPrivateServerDisplayName() throws Exception
	{
		server.enqueue(202, "{\"member\":{\"id\":\"member-2\",\"groupId\":\"group-1\","
			+ "\"label\":\"Member 123456\",\"role\":\"member\",\"status\":\"pending\","
			+ "\"token\":\"secret-token\"}}");

		HostedApiClient.JoinResponse response = api.joinGroup("group-1", "ABCD-EFGH-JK23",
			"Sqwiglyy", "solo");
		assertEquals("Member 123456", response.member.label);

		CapturedRequest request = server.takeRequest();
		assertTrue(request.body.contains("\"groupId\":\"group-1\""));
		assertTrue(request.body.contains("\"inviteCode\":\"ABCD-EFGH-JK23\""));
		assertTrue(request.body.contains("\"playerName\":\"Sqwiglyy\""));
		assertTrue(request.body.contains("\"collectionMode\":\"solo\""));
	}

	@Test
	public void uploadsOnlyRequiredCardProvenanceAndSurfacesApiErrors() throws Exception
	{
		server.enqueue(200, "{\"accepted\":1}");
		api.uploadMemberCollection(profile(), "snapshot_123", List.of(
			new HostedApiClient.CardInstanceUpload("i_opaque", "Great Olm", true, false, 1000L)),
			true, "solo");

		CapturedRequest request = server.takeRequest();
		assertEquals("/v1/groups/group-1/member-collection", request.path);
		assertTrue(request.body.contains("\"sourceInstanceId\":\"i_opaque\""));
		assertTrue(request.body.contains("\"foil\":true"));
		assertTrue(request.body.contains("\"debug\":false"));
		assertTrue(request.body.contains("\"collectionMode\":\"solo\""));
		assertFalse(request.body.contains("pulledBy"));
		assertFalse(request.body.contains("Example Player"));

		server.enqueue(403, "{\"error\":{\"code\":\"approval_required\","
			+ "\"message\":\"Owner approval required\"}}");
		try
		{
			api.getGroup(profile());
			fail("Expected hosted API failure");
		}
		catch (HostedApiException ex)
		{
			assertEquals(403, ex.status());
			assertEquals("approval_required", ex.code());
			assertEquals("Owner approval required", ex.getMessage());
		}
	}

	@Test
	public void createsAndRespondsToServerTopTrumpsChallenges() throws Exception
	{
		server.enqueue(201, "{\"challengeId\":\"duel-123456\",\"expiresAt\":9999}");
		HostedApiClient.TopTrumpsChallengeResponse response =
			api.createTopTrumpsChallenge(profile(), "member-2");
		assertEquals("duel-123456", response.challengeId);
		CapturedRequest create = server.takeRequest();
		assertEquals("/v1/groups/group-1/top-trumps/challenges", create.path);
		assertTrue(create.body.contains("\"targetMemberId\":\"member-2\""));

		server.enqueue(200, "{\"challengeId\":\"duel-123456\",\"status\":\"accepted\"}");
		api.respondTopTrumpsChallenge(profile(), "duel-123456", true);
		CapturedRequest accept = server.takeRequest();
		assertEquals("/v1/groups/group-1/top-trumps/challenges/duel-123456/response", accept.path);
		assertTrue(accept.body.contains("\"accepted\":true"));
	}

	@Test
	public void revokesOnlyTheSelectedHostedMember() throws Exception
	{
		server.enqueue(200, "{\"memberId\":\"member-2\",\"revokedAt\":1234}");
		api.revokeMember(profile(), "member-2");

		CapturedRequest request = server.takeRequest();
		assertEquals("DELETE", request.method);
		assertEquals("/v1/groups/group-1/members/member-2", request.path);
		assertEquals("Bearer member-token", request.header("Authorization"));
	}

	@Test
	public void keepsAStoredTokenBoundToTheServerThatIssuedIt() throws Exception
	{
		try (TestHttpServer configuredServer = new TestHttpServer();
			 TestHttpServer profileServer = new TestHttpServer())
		{
			GroupmanTcgConfig customConfig = new GroupmanTcgConfig()
			{
				@Override
				public String hostedServerUrl()
				{
					return configuredServer.baseUrl();
				}
			};
			HostedApiClient configuredApi = new HostedApiClient(new OkHttpClient(), new Gson(), customConfig);
			HostedProfile profile = profile();
			profile.serverUrl = profileServer.baseUrl();
			profileServer.enqueue(200, "{\"group\":{\"id\":\"group-1\"},\"members\":[]}");

			configuredApi.getGroup(profile);

			CapturedRequest request = profileServer.takeRequest();
			assertEquals("Bearer member-token", request.header("Authorization"));
			assertEquals(0, configuredServer.requestCount());
		}
	}

	@Test
	public void rejectsUnencryptedRemoteServers()
	{
		try
		{
			new HostedApiClient(new OkHttpClient(), new Gson(), "http://example.com");
			fail("Expected insecure server URL to be rejected");
		}
		catch (IllegalArgumentException ex)
		{
			assertTrue(ex.getMessage().contains("HTTPS"));
		}
	}

	@Test
	public void leavesHostedServerUnconfiguredByDefault()
	{
		GroupmanTcgConfig config = new GroupmanTcgConfig()
		{
		};

		assertEquals("", config.hostedServerUrl());
		assertFalse(config.hostedSyncEnabled());
		assertFalse(config.downloadCardArt());
	}

	private static HostedProfile profile()
	{
		HostedProfile profile = new HostedProfile();
		profile.groupId = "group-1";
		profile.memberId = "member-1";
		profile.rsn = "local-only-name";
		profile.role = "owner";
		profile.status = "approved";
		profile.token = "member-token";
		return profile;
	}

	private static final class TestHttpServer implements AutoCloseable
	{
		private final HttpServer server;
		private final Queue<ResponseSpec> responses = new ArrayDeque<>();
		private final LinkedBlockingQueue<CapturedRequest> requests = new LinkedBlockingQueue<>();

		private TestHttpServer() throws IOException
		{
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", this::handle);
			server.start();
		}

		private String baseUrl()
		{
			return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
		}

		private synchronized void enqueue(int status, String body)
		{
			responses.add(new ResponseSpec(status, body));
		}

		private CapturedRequest takeRequest() throws InterruptedException
		{
			CapturedRequest request = requests.poll(5, TimeUnit.SECONDS);
			if (request == null)
			{
				fail("Timed out waiting for HTTP request");
			}
			return request;
		}

		private int requestCount()
		{
			return requests.size();
		}

		private void handle(HttpExchange exchange) throws IOException
		{
			byte[] requestBody = exchange.getRequestBody().readAllBytes();
			requests.add(new CapturedRequest(exchange.getRequestMethod(),
				exchange.getRequestURI().toString(), new String(requestBody, StandardCharsets.UTF_8),
				exchange.getRequestHeaders()));
			ResponseSpec response;
			synchronized (this)
			{
				response = responses.poll();
			}
			if (response == null)
			{
				response = new ResponseSpec(500, "{\"error\":{\"message\":\"No test response queued\"}}");
			}
			byte[] bytes = response.body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(response.status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		}

		@Override
		public void close()
		{
			server.stop(0);
		}
	}

	private static final class CapturedRequest
	{
		private final String method;
		private final String path;
		private final String body;
		private final Map<String, List<String>> headers;

		private CapturedRequest(String method, String path, String body, Map<String, List<String>> headers)
		{
			this.method = method;
			this.path = path;
			this.body = body;
			this.headers = headers;
		}

		private String header(String name)
		{
			for (Map.Entry<String, List<String>> entry : headers.entrySet())
			{
				if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty())
				{
					return entry.getValue().get(0);
				}
			}
			return null;
		}
	}

	private static final class ResponseSpec
	{
		private final int status;
		private final String body;

		private ResponseSpec(int status, String body)
		{
			this.status = status;
			this.body = body;
		}
	}
}
