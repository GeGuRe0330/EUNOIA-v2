package com.eunoia.identity.domain;

public interface ProfileImageProcessor {
    byte[] toProfileJpeg(byte[] original);
}
