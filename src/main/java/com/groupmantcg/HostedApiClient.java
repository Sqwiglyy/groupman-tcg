package com.groupmantcg;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Synchronous API adapter. Callers run it only on RuneLite's background executor. */
@Singleton
class HostedApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;
	private final GroupmanTcgConfig config;
	private final HttpUrl fixedBaseUrl;

	@Inject
	HostedApiClient(OkHttpClient http, Gson gson, GroupmanTcgConfig config)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		this.fixedBaseUrl = null;
	}

	HostedApiClient(OkHttpClient http, Gson gson, String baseUrl)
	{
		this.http = http;
		this.gson = gson;
		this.config = null;
		this.fixedBaseUrl = parseBaseUrl(baseUrl);
	}

	CreateResponse createGroup(String setupKey, String playerName, String collectionMode) throws IOException
	{
		return createGroupAt(configuredBaseUrl(), setupKey, playerName, collectionMode);
	}

	CreateResponse createGroupAt(String serverUrl, String setupKey, String playerName,
		String collectionMode) throws IOException
	{
		return call("POST", url(serverUrl, "v1", "groups"),
			new MembershipRequest(playerName, collectionMode), null,
			CreateResponse.class, setupKey);
	}

	JoinResponse joinGroup(String groupId, String inviteCode, String playerName,
		String collectionMode) throws IOException
	{
		return joinGroupAt(configuredBaseUrl(), groupId, inviteCode, playerName, collectionMode);
	}

	JoinResponse joinGroupAt(String serverUrl, String groupId, String inviteCode,
		String playerName, String collectionMode) throws IOException
	{
		return call("POST", url(serverUrl, "v1", "join"),
			new JoinRequest(groupId, inviteCode, playerName, collectionMode), null,
			JoinResponse.class);
	}

	GroupResponse getGroup(HostedProfile profile) throws IOException
	{
		return call("GET", url(profile, "v1", "groups", profile.groupId), null, profile, GroupResponse.class);
	}

	void approveMember(HostedProfile profile, String memberId) throws IOException
	{
		call("POST", url(profile, "v1", "groups", profile.groupId, "members", memberId),
			new EmptyRequest(), profile, EmptyResponse.class);
	}

	void revokeMember(HostedProfile profile, String memberId) throws IOException
	{
		call("DELETE", url(profile, "v1", "groups", profile.groupId, "members", memberId),
			new EmptyRequest(), profile, EmptyResponse.class);
	}

	InviteResponse rotateInvite(HostedProfile profile) throws IOException
	{
		return call("POST", url(profile, "v1", "groups", profile.groupId, "invite"),
			new EmptyRequest(), profile, InviteResponse.class);
	}

	void uploadMemberCollection(HostedProfile profile, String snapshotId,
		List<CardInstanceUpload> instances, boolean complete, String collectionMode) throws IOException
	{
		call("POST", url(profile, "v1", "groups", profile.groupId, "member-collection"),
			new MemberCollectionRequest(snapshotId, complete, collectionMode, instances),
			profile, EmptyResponse.class);
	}

	void uploadPack(HostedProfile profile, PackUpload pack) throws IOException
	{
		call("POST", url(profile, "v1", "groups", profile.groupId, "packs"), pack, profile, EmptyResponse.class);
	}

	SyncResponse sync(HostedProfile profile, long cursor, long topTrumpsCursor,
		long collectionVersion) throws IOException
	{
		HttpUrl target = url(profile, "v1", "groups", profile.groupId, "sync").newBuilder()
			.addQueryParameter("after", Long.toString(Math.max(0L, cursor)))
			.addQueryParameter("topTrumpsAfter", Long.toString(Math.max(0L, topTrumpsCursor)))
			.addQueryParameter("collectionVersion", Long.toString(Math.max(0L, collectionVersion)))
			.addQueryParameter("limit", "100")
			.build();
		return call("GET", target, null, profile, SyncResponse.class);
	}

	TopTrumpsChallengeResponse createTopTrumpsChallenge(HostedProfile profile,
		String targetMemberId) throws IOException
	{
		return call("POST", url(profile, "v1", "groups", profile.groupId,
			"top-trumps", "challenges"), new TopTrumpsChallengeRequest(targetMemberId),
			profile, TopTrumpsChallengeResponse.class);
	}

	void respondTopTrumpsChallenge(HostedProfile profile, String challengeId,
		boolean accepted) throws IOException
	{
		call("POST", url(profile, "v1", "groups", profile.groupId,
			"top-trumps", "challenges", challengeId, "response"),
			new TopTrumpsResponseRequest(accepted), profile, EmptyResponse.class);
	}

	MemberCollectionsResponse getMemberCollections(HostedProfile profile) throws IOException
	{
		return call("GET", url(profile, "v1", "groups", profile.groupId, "member-collections"),
			null, profile, MemberCollectionsResponse.class);
	}

	MemberCollectionResponse getMemberCollection(HostedProfile profile, String memberId, int offset) throws IOException
	{
		HttpUrl target = url(profile, "v1", "groups", profile.groupId, "members", memberId, "collection").newBuilder()
			.addQueryParameter("offset", Integer.toString(Math.max(0, offset)))
			.addQueryParameter("limit", "200")
			.build();
		return call("GET", target, null, profile, MemberCollectionResponse.class);
	}

	String configuredBaseUrl() throws IOException
	{
		return resolveBaseUrl(null).toString();
	}

	private HttpUrl url(HostedProfile profile, String... segments) throws IOException
	{
		HttpUrl.Builder builder = resolveBaseUrl(profile).newBuilder();
		for (String segment : segments)
		{
			builder.addPathSegment(segment);
		}
		return builder.build();
	}

	private HttpUrl url(String serverUrl, String... segments) throws IOException
	{
		HttpUrl base;
		try
		{
			base = parseBaseUrl(serverUrl);
		}
		catch (IllegalArgumentException ex)
		{
			throw new IOException(ex.getMessage(), ex);
		}
		HttpUrl.Builder builder = base.newBuilder();
		for (String segment : segments)
		{
			builder.addPathSegment(segment);
		}
		return builder.build();
	}

	private HttpUrl resolveBaseUrl(HostedProfile profile) throws IOException
	{
		if (fixedBaseUrl != null)
		{
			return fixedBaseUrl;
		}
		String configured = profile != null && profile.serverUrl != null && !profile.serverUrl.trim().isEmpty()
			? profile.serverUrl : config.hostedServerUrl();
		try
		{
			return parseBaseUrl(configured);
		}
		catch (IllegalArgumentException ex)
		{
			throw new IOException(ex.getMessage(), ex);
		}
	}

	private static HttpUrl parseBaseUrl(String value)
	{
		HttpUrl parsed = value == null ? null : HttpUrl.parse(value.trim());
		if (parsed == null || parsed.host().isEmpty() || !parsed.username().isEmpty()
			|| !parsed.password().isEmpty() || parsed.querySize() > 0 || parsed.fragment() != null
			|| !"/".equals(parsed.encodedPath()))
		{
			throw new IllegalArgumentException("Server URL must be a root such as https://example.workers.dev");
		}
		boolean local = "localhost".equals(parsed.host()) || "127.0.0.1".equals(parsed.host())
			|| "::1".equals(parsed.host());
		if (!"https".equals(parsed.scheme()) && !(local && "http".equals(parsed.scheme())))
		{
			throw new IllegalArgumentException("Server URL must use HTTPS (HTTP is allowed only for localhost)");
		}
		return parsed;
	}

	private <T> T call(String method, HttpUrl url, Object body, HostedProfile profile,
		Class<T> responseType) throws IOException
	{
		return call(method, url, body, profile, responseType, null);
	}

	private <T> T call(String method, HttpUrl url, Object body, HostedProfile profile,
		Class<T> responseType, String setupKey) throws IOException
	{
		Request.Builder request = new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.header("User-Agent", "Group-TCG/0.1.1");
		if (setupKey != null && !setupKey.isEmpty())
		{
			request.header("X-Groupman-Setup-Key", setupKey);
		}
		if (profile != null)
		{
			request.header("Authorization", "Bearer " + profile.token);
		}
		if ("GET".equals(method))
		{
			request.get();
		}
		else
		{
			request.method(method, RequestBody.create(JSON, gson.toJson(body)));
		}

		try (Response response = http.newCall(request.build()).execute())
		{
			ResponseBody responseBody = response.body();
			String json = responseBody == null ? "" : responseBody.string();
			if (!response.isSuccessful())
			{
				ErrorEnvelope error = parseError(json);
				throw new HostedApiException(response.code(), error.code,
					error.message == null ? "The hosted service returned HTTP " + response.code() : error.message);
			}
			if (responseType == EmptyResponse.class || json.isEmpty())
			{
				return responseType.cast(new EmptyResponse());
			}
			T parsed = gson.fromJson(json, responseType);
			if (parsed == null)
			{
				throw new IOException("The hosted service returned an empty response");
			}
			return parsed;
		}
	}

	private ErrorEnvelope parseError(String json)
	{
		try
		{
			ErrorResponse response = gson.fromJson(json, ErrorResponse.class);
			return response != null && response.error != null ? response.error : new ErrorEnvelope();
		}
		catch (RuntimeException ex)
		{
			return new ErrorEnvelope();
		}
	}

	static final class CreateResponse
	{
		GroupRef group;
		MemberRef member;
		Invite invite;
	}

	static final class JoinResponse
	{
		MemberRef member;
	}

	static final class GroupResponse
	{
		GroupRef group;
		MemberRef currentMember;
		List<MemberRef> members = Collections.emptyList();
	}

	static final class InviteResponse
	{
		Invite invite;
	}

	static final class MemberCollectionsResponse
	{
		List<MemberSummary> members = Collections.emptyList();
	}

	static final class MemberCollectionResponse
	{
		MemberRef member;
		int nextOffset;
		boolean hasMore;
		List<CardInstanceResult> instances = Collections.emptyList();
	}

	static final class SyncResponse
	{
		long nextCursor;
		boolean hasMore;
		List<PackEvent> events = Collections.emptyList();
		long topTrumpsNextCursor;
		List<TopTrumpsEvent> topTrumpsEvents = Collections.emptyList();
		CollectionResult collection;
	}

	static final class GroupRef
	{
		String id;
		long collectionVersion;
	}

	static final class MemberRef
	{
		String id;
		String groupId;
		String label;
		String playerName;
		String collectionMode;
		String role;
		String status;
		String token;
		boolean revoked;
	}

	static final class TopTrumpsChallengeResponse
	{
		String challengeId;
		long expiresAt;
	}

	static final class TopTrumpsEvent
	{
		long sequence;
		String challengeId;
		String type;
		long createdAt;
		long expiresAt;
		MemberRef challenger;
		MemberRef challenged;
		String challengerCard;
		String challengedCard;
	}

	static final class Invite
	{
		String code;
		long expiresAt;
	}

	static final class MemberSummary
	{
		String id;
		String label;
		String playerName;
		String collectionMode;
		int cards;
		int copies;
		int foils;
	}

	static final class CardInstanceResult
	{
		String sourceInstanceId;
		String cardName;
		boolean foil;
		long pulledAt;
		String acquisitionKind;
	}

	static final class PackEvent
	{
		long sequence;
		String eventId;
		long openedAt;
		long receivedAt;
		MemberRef member;
		List<CardPullResult> cards = Collections.emptyList();
	}

	static final class CardPullResult
	{
		String name;
		boolean foil;
		boolean isNew;
	}

	static final class CollectionResult
	{
		long version;
		boolean changed;
		List<String> unlocks;
	}

	static final class CardInstanceUpload
	{
		final String sourceInstanceId;
		final String cardName;
		final boolean foil;
		final boolean debug;
		final long pulledAt;

		CardInstanceUpload(String sourceInstanceId, String cardName, boolean foil, boolean debug, long pulledAt)
		{
			this.sourceInstanceId = sourceInstanceId;
			this.cardName = cardName;
			this.foil = foil;
			this.debug = debug;
			this.pulledAt = pulledAt;
		}
	}

	static final class PackUpload
	{
		final String eventId;
		final long openedAt;
		final List<CardPullUpload> cards;

		PackUpload(String eventId, long openedAt, List<CardPullUpload> cards)
		{
			this.eventId = eventId;
			this.openedAt = openedAt;
			this.cards = new ArrayList<>(cards);
		}
	}

	static final class CardPullUpload
	{
		final String name;
		final boolean foil;
		final boolean isNew;

		CardPullUpload(String name, boolean foil, boolean isNew)
		{
			this.name = name;
			this.foil = foil;
			this.isNew = isNew;
		}
	}

	private static final class JoinRequest
	{
		final String groupId;
		final String inviteCode;
		final String playerName;
		final String collectionMode;

		private JoinRequest(String groupId, String inviteCode, String playerName,
			String collectionMode)
		{
			this.groupId = groupId;
			this.inviteCode = inviteCode;
			this.playerName = playerName;
			this.collectionMode = collectionMode;
		}
	}

	private static final class MembershipRequest
	{
		final String playerName;
		final String collectionMode;

		private MembershipRequest(String playerName, String collectionMode)
		{
			this.playerName = playerName;
			this.collectionMode = collectionMode;
		}
	}

	private static final class MemberCollectionRequest
	{
		final String snapshotId;
		final boolean complete;
		final String collectionMode;
		final List<CardInstanceUpload> instances;

		private MemberCollectionRequest(String snapshotId, boolean complete,
			String collectionMode, List<CardInstanceUpload> instances)
		{
			this.snapshotId = snapshotId;
			this.complete = complete;
			this.collectionMode = collectionMode;
			this.instances = new ArrayList<>(instances);
		}
	}

	private static final class TopTrumpsChallengeRequest
	{
		final String targetMemberId;

		private TopTrumpsChallengeRequest(String targetMemberId)
		{
			this.targetMemberId = targetMemberId;
		}
	}

	private static final class TopTrumpsResponseRequest
	{
		final boolean accepted;

		private TopTrumpsResponseRequest(boolean accepted)
		{
			this.accepted = accepted;
		}
	}

	private static final class ErrorResponse
	{
		ErrorEnvelope error;
	}

	private static final class ErrorEnvelope
	{
		String code = "api_error";
		String message;
	}

	private static final class EmptyResponse
	{
	}

	private static final class EmptyRequest
	{
	}
}
