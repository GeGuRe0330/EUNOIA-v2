package com.eunoia.identity.infrastructure.security;

import com.eunoia.common.security.AuthenticatedPrincipal;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.Status;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomMemberDetails implements UserDetails, AuthenticatedPrincipal {

    private final Member member;

    public CustomMemberDetails(Member member) {
        this.member = member;
    }

    public Member getMember() {
        return member;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
    }

    @Override
    public String getUsername() {
        return member.getEmail();
    }

    @Override
    public @Nullable String getPassword() {
        return member.getPassword();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return member.getStatus() == Status.ACTIVE;
    }

    @Override
    public Long getMemberId() {
        return member.getId();
    }
}
