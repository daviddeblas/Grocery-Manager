package com.grocerymanager.api.service;

import com.grocerymanager.api.exception.TokenRefreshException;
import com.grocerymanager.api.model.RefreshToken;
import com.grocerymanager.api.repository.RefreshTokenRepository;
import com.grocerymanager.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {
    @Value("${jwt.refresh-expiration}")
    private Long refreshTokenDurationMs;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    public RefreshToken createRefreshToken(Long userId) {
        // Check if a token already exists for this user
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUser(
                userRepository.findById(userId).get()
        );

        RefreshToken refreshToken;

        if (existingToken.isPresent()) {
            // Update existing token with new expiry and token value
            refreshToken = existingToken.get();
            refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
            refreshToken.setToken(UUID.randomUUID().toString());
        } else {
            // Create new token if one doesn't exist
            refreshToken = new RefreshToken();
            refreshToken.setUser(userRepository.findById(userId).get());
            refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
            refreshToken.setToken(UUID.randomUUID().toString());
        }

        // Save the token (both for new and updated tokens)
        refreshToken = refreshTokenRepository.save(refreshToken);
        return refreshToken;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("Refresh token was expired. Please sign in again");
        }

        return token;
    }
}