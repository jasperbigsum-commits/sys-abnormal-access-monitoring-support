package io.github.jasper.monitoring.api.authentication;

/** Host extension point that maps login aliases to one canonical account subject. */
public interface LoginSubjectCanonicalizer {
    String canonicalize(LoginSubjectInput subject);
}
