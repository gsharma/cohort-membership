package com.github.membership.server;

/**
 * Unified single exception that's thrown and handled by various server-side components of MembershipService. The idea is to use the code enum to
 * encapsulate various error/exception conditions. That said, stack traces, where available and desired, are not meant to be kept from users.
 */
final class MembershipServerException extends Exception {
    private static final long serialVersionUID = 1L;
    private final Code code;

    MembershipServerException(final Code code) {
        super(code.getDescription());
        this.code = code;
    }

    MembershipServerException(final Code code, final String message) {
        super(message);
        this.code = code;
    }

    MembershipServerException(final Code code, final Throwable throwable) {
        super(throwable);
        this.code = code;
    }

    MembershipServerException(final Code code, final String message, final Throwable throwable) {
        super(message, throwable);
        this.code = code;
    }

    Code getCode() {
        return code;
    }

    static enum Code {
        // 1.
        INVALID_MEMBERSHIP_LCM("Membership service cannot retransition to the same desired state"),
        // 2.
        MEMBERSHIP_INIT_FAILURE("Failed to initialize membership service"),
        // 3.
        MEMBERSHIP_TINI_FAILURE("Failed to cleanly shutdown membership service"),
        // 4.
        REQUEST_VALIDATION_FAILURE("Membership service received an invalid request"),
        // 5.
        PARENT_LOCATOR_FAILURE("Membership service failed to locate parent entity"),
        // 6.
        LOCK_ACQUISITION_FAILURE("Membership service failed to acquire lock on entity"),
        // 7.
        LOCK_RELEASE_FAILURE("Membership service failed to release lock on entity"),
        // 8.
        EXPECTED_VERSION_MISMATCH("Membership service observed an expected version mismatch"),
        // n.
        UNKNOWN_FAILURE(
                "Membership service internal failure. Check exception stacktrace for more details of the failure");

        private String description;

        private Code(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

}
