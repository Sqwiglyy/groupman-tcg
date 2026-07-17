package com.groupmantcg;

import com.google.gson.Gson;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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
	private MockWebServer server;
	private HostedApiClient api;

	@Before
	public void setUp() throws Exception
	{
		server = new MockWebServer();
		server.start();
		api = new HostedApiClient(new OkHttpClient(), new Gson(), server.url("/").toString());
	}

	@After
	public void tearDown() throws Exception
	{
		server.shutdown();
	}

	@Test
	public void createsGroupWithoutLeakingAnAuthorizationHeader() throws Exception
	{
		server.enqueue(json(201, "{\"group\":{\"id\":\"group-1\",\"displayName\":\"Sqwiggles\"},"
			+ "\"member\":{\"id\":\"member-1\",\"rsn\":\"Sqwiglyy\",\"role\":\"owner\","
			+ "\"status\":\"approved\",\"token\":\"secret-token\"},"
			+ "\"invite\":{\"code\":\"ABCD-EFGH-JK23\",\"expiresAt\":1234}}"));

		HostedApiClient.CreateResponse response = api.createGroup("Sqwiggles", "Sqwiglyy");
		assertEquals("group-1", response.group.id);
		assertEquals("secret-token", response.member.token);

		RecordedRequest request = server.takeRequest();
		assertEquals("/v1/groups", request.getPath());
		assertEquals("POST", request.getMethod());
		assertNull(request.getHeader("Authorization"));
		assertTrue(request.getBody().readUtf8().contains("\"ownerRsn\":\"Sqwiglyy\""));
	}

	@Test
	public void authenticatesSyncAndParsesMissedPacks() throws Exception
	{
		server.enqueue(json(200, "{\"nextCursor\":9,\"hasMore\":false,\"events\":[{"
			+ "\"sequence\":9,\"eventId\":\"pack_9\",\"member\":{\"id\":\"other\",\"rsn\":\"Mate\"},"
			+ "\"cards\":[{\"name\":\"Great Olm\",\"foil\":true,\"isNew\":true}]}],"
			+ "\"collection\":{\"version\":4,\"changed\":true,\"unlocks\":[\"Great Olm\"]}}"));

		HostedApiClient.SyncResponse response = api.sync(profile(), 7L, 3L);
		assertEquals(9L, response.nextCursor);
		assertFalse(response.hasMore);
		assertEquals("Great Olm", response.events.get(0).cards.get(0).name);

		RecordedRequest request = server.takeRequest();
		assertEquals("Bearer member-token", request.getHeader("Authorization"));
		assertTrue(request.getPath().startsWith("/v1/groups/group-1/sync?"));
		assertTrue(request.getRequestUrl().queryParameter("after").equals("7"));
		assertTrue(request.getRequestUrl().queryParameter("collectionVersion").equals("3"));
	}

	@Test
	public void uploadsExactCardProvenanceAndSurfacesApiErrors() throws Exception
	{
		server.enqueue(json(200, "{\"accepted\":1}"));
		api.uploadMemberCollection(profile(), "snapshot_123", List.of(
			new HostedApiClient.CardInstanceUpload("instance-1", "Great Olm", true, "Sqwiglyy", 1000L)), true);

		RecordedRequest request = server.takeRequest();
		String body = request.getBody().readUtf8();
		assertEquals("/v1/groups/group-1/member-collection", request.getPath());
		assertTrue(body.contains("\"sourceInstanceId\":\"instance-1\""));
		assertTrue(body.contains("\"foil\":true"));
		assertTrue(body.contains("\"complete\":true"));

		server.enqueue(json(403, "{\"error\":{\"code\":\"approval_required\","
			+ "\"message\":\"Owner approval required\"}}"));
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
	public void revokesOnlyTheSelectedHostedMember() throws Exception
	{
		server.enqueue(json(200, "{\"memberId\":\"member-2\",\"revokedAt\":1234}"));
		api.revokeMember(profile(), "member-2");

		RecordedRequest request = server.takeRequest();
		assertEquals("DELETE", request.getMethod());
		assertEquals("/v1/groups/group-1/members/member-2", request.getPath());
		assertEquals("Bearer member-token", request.getHeader("Authorization"));
	}

	@Test
	public void keepsAStoredTokenBoundToTheServerThatIssuedIt() throws Exception
	{
		try (MockWebServer configuredServer = new MockWebServer();
			 MockWebServer profileServer = new MockWebServer())
		{
			configuredServer.start();
			profileServer.start();
			GroupmanTcgConfig customConfig = new GroupmanTcgConfig()
			{
				@Override
				public String hostedServerUrl()
				{
					return configuredServer.url("/").toString();
				}
			};
			HostedApiClient configuredApi = new HostedApiClient(new OkHttpClient(), new Gson(), customConfig);
			HostedProfile profile = profile();
			profile.serverUrl = profileServer.url("/").toString();
			profileServer.enqueue(json(200, "{\"group\":{\"id\":\"group-1\"},\"members\":[]}"));

			configuredApi.getGroup(profile);

			RecordedRequest request = profileServer.takeRequest();
			assertEquals("Bearer member-token", request.getHeader("Authorization"));
			assertEquals(0, configuredServer.getRequestCount());
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

	private static HostedProfile profile()
	{
		HostedProfile profile = new HostedProfile();
		profile.groupId = "group-1";
		profile.memberId = "member-1";
		profile.rsn = "Sqwiglyy";
		profile.role = "owner";
		profile.status = "approved";
		profile.token = "member-token";
		return profile;
	}

	private static MockResponse json(int status, String body)
	{
		return new MockResponse().setResponseCode(status)
			.setHeader("content-type", "application/json")
			.setBody(body);
	}
}
