package com.chronos.service;

import com.chronos.model.pojo.IdentitySource;

public interface OidcClientSecretProvider {
	String resolve(IdentitySource source);
}
