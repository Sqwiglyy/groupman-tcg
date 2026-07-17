package com.groupmantcg;

import java.io.IOException;

final class HostedApiException extends IOException
{
	private final int status;
	private final String code;

	HostedApiException(int status, String code, String message)
	{
		super(message);
		this.status = status;
		this.code = code == null ? "api_error" : code;
	}

	int status()
	{
		return status;
	}

	String code()
	{
		return code;
	}
}
