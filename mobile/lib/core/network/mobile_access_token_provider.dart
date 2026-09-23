abstract interface class MobileAccessTokenProvider {
  String? get accessToken;

  /// Refreshes the in-memory access token with the OS-protected refresh token.
  Future<bool> refreshAccessToken();

  /// Removes local session state after the server rejects the mobile identity.
  Future<void> invalidateSession();
}
