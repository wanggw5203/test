package io.testkit.lab;

import io.testkit.basetest.identity.IdentityProvider;
import io.testkit.basetest.identity.UserIdentity;
import java.util.Map;

/** Listener acquires a real session before each annotated test and revokes it afterwards. */
public final class LoginIdentityProvider implements IdentityProvider {
  private final ApiClient api;

  public LoginIdentityProvider(ApiClient api) {
    this.api = api;
  }

  @Override
  public UserIdentity acquire(String profile) {
    return new UserIdentity(
        profile,
        profile,
        api.login(profile),
        Map.of("role", profile.equals("admin") ? "ADMIN" : "USER"));
  }

  @Override
  public void release(UserIdentity identity) {
    ApiClient.Response response =
        api.call("POST", "/api/auth/logout", identity.accessToken(), null);
    if (response.status() != 200 && response.status() != 401)
      throw new IllegalStateException("Logout failed: HTTP " + response.status());
  }
}
