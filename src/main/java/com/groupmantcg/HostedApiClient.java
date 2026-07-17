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
	static final String PRODUCTION_URL = "https://groupman-tcg-api.sqwiglyy.workers.dev";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;
	private final HttpUrl baseUrl;

	@Inject
	HostedApiClient(OkHttpClient http, Gson gson)
	{
		this(http, gson, PRODUCTION_URL);
	}

	HostedApiClient(OkHttpClient http, Gson gson, String baseUrl)
	{
		this.http = http;
		this.gson = gson;
		this.baseUrl = HttpUrl.parse(baseUrl);
		if (this.baseUrl == null || !"https".equals(this.baseUrl.scheme()) && !"http".equals(this.baseUrl.scheme()))
		{
			throw new IllegalArgumentException("Invalid API base URL");
		}
	}

	CreateResponse createGroup(String groupName, String ownerRsn) throws IOException
	{
		return call("POST", url("v1", "groups"), new CreateRequest(groupName, ownerRsn), null,
			CreateResponse.class);
	}

	JoinResponse joinGroup(String groupId, String rsn, String inviteCode) throws IOException
	{
		return call("POST", url("v1", "join"), new JoinRequest(groupId, rsn, inviteCode), null,
			JoinResponse.class);
	}

	GroupResponse getGroup(HostedProfile profile) throws IOException
	{
		return call("GET", url("v1", "groups", profile.groupId), null, profile, GroupResponse.class);
	}

	void approveMember(HostedProfile profile, String memberId) throws IOException
	{
		call("POST", url("v1", "groups", profile.groupId, "members", memberId),
			Collections.emptyMap(), profile, EmptyResponse.class);
	}

	void revokeMember(HostedProfile profile, String memberId) throws IOException
	{
		call("DELETE", url("v1", "groups", profile.groupId, "members", memberId),
			Collections.emptyMap(), profile, EmptyResponse.class);
	}

	InviteResponse rotateInvite(HostedProfile profile) throws IOException
	{
		return call("POST", url("v1", "groups", profile.groupId, "invite"),
			Collections.emptyMap(), profile, InviteResponse.class);
	}

	void uploadMemberCollection(HostedProfile profile, String snapshotId,
		List<CardInstanceUpload> instances, boolean complete) throws IOException
	{
		call("POST", url("v1", "groups", profile.groupId, "member-collection"),
			new MemberCollectionRequest(snapshotId, complete, instances), profile, EmptyResponse.class);
	}

	void uploadPack(HostedProfile profile, PackUpload pack) throws IOException
	{
		call("POST", url("v1", "groups", profile.groupId, "packs"), pack, profile, EmptyResponse.class);
	}

	SyncResponse sync(HostedProfile profile, long cursor, long collectionVersion) throws IOException
	{
		HttpUrl target = url("v1", "groups", profile.groupId, "sync").newBuilder()
			.addQueryParameter("after", Long.toString(Math.max(0L, cursor)))
			.addQueryParameter("collectionVersion", Long.toString(Math.max(0L, collectionVersion)))
			.addQueryParameter("limit", "100")
			.build();
		return call("GET", target, null, profile, SyncResponse.class);
	}

	MemberCollectionsResponse getMemberCollections(HostedProfile profile) throws IOException
	{
		return call("GET", url("v1", "groups", profile.groupId, "member-collections"),
			null, profile, MemberCollectionsResponse.class);
	}

	MemberCollectionResponse getMemberCollection(HostedProfile profile, String memberId, int offset) throws IOException
	{
		HttpUrl target = url("v1", "groups", profile.groupId, "members", memberId, "collection").newBuilder()
			.addQueryParameter("offset", Integer.toString(Math.max(0, offset)))
			.addQueryParameter("limit", "200")
			.build();
		return call("GET", target, null, profile, MemberCollectionResponse.class);
	}

	private HttpUrl url(String... segments)
	{
		HttpUrl.Builder builder = baseUrl.newBuilder();
		for (String segment : segments)
		{
			builder.addPathSegment(segment);
		}
		return builder.build();
	}

	private <T> T call(String method, HttpUrl url, Object body, HostedProfile profile,
		Class<T> responseType) throws IOException
	{
		Request.Builder request = new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.header("User-Agent", "Groupman-TCG/0.1.0");
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
		CollectionResult collection;
	}

	static final class GroupRef
	{
		String id;
		String displayName;
		long collectionVersion;
	}

	static final class MemberRef
	{
		String id;
		String groupId;
		String rsn;
		String role;
		String status;
		String token;
		boolean revoked;
	}

	static final class Invite
	{
		String code;
		long expiresAt;
	}

	static final class MemberSummary
	{
		String id;
		String rsn;
		int cards;
		int copies;
		int foils;
	}

	static final class CardInstanceResult
	{
		String sourceInstanceId;
		String cardName;
		boolean foil;
		String pulledBy;
		long pulledAt;
		String acquisitionKind;
	}

	static final class PackEvent
	{
		long sequence;
		String eventId;
		long openedAt;
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
		final String pulledBy;
		final long pulledAt;

		CardInstanceUpload(String sourceInstanceId, String cardName, boolean foil, String pulledBy, long pulledAt)
		{
			this.sourceInstanceId = sourceInstanceId;
			this.cardName = cardName;
			this.foil = foil;
			this.pulledBy = pulledBy;
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

	private static final class CreateRequest
	{
		final String groupName;
		final String ownerRsn;

		private CreateRequest(String groupName, String ownerRsn)
		{
			this.groupName = groupName;
			this.ownerRsn = ownerRsn;
		}
	}

	private static final class JoinRequest
	{
		final String groupId;
		final String rsn;
		final String inviteCode;

		private JoinRequest(String groupId, String rsn, String inviteCode)
		{
			this.groupId = groupId;
			this.rsn = rsn;
			this.inviteCode = inviteCode;
		}
	}

	private static final class MemberCollectionRequest
	{
		final String snapshotId;
		final boolean complete;
		final List<CardInstanceUpload> instances;

		private MemberCollectionRequest(String snapshotId, boolean complete, List<CardInstanceUpload> instances)
		{
			this.snapshotId = snapshotId;
			this.complete = complete;
			this.instances = new ArrayList<>(instances);
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
}
