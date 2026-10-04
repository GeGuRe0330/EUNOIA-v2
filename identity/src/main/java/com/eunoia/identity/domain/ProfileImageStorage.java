package com.eunoia.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface ProfileImageStorage {
    void save(UUID imageId, byte[] content);

    Optional<byte[]> read(UUID imageId);

    void delete(UUID imageId);
}
