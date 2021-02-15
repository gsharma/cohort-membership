package com.github.membership.client;

/**
 * Unified single exception that's thrown and handled by various client-side components of Membership Service. The idea is to use the code enum to
 * encapsulate various error/exception conditions. That said, stack traces, where available and desired, are not meant to be kept from users.
 */
public final class MembershipClientException extends Exception {
    private static final long serialVersionUID = 1L;
    private final Code code;

    public MembershipClientException(final Code code) {
        super(code.getDescription());
        this.code = code;
    }

    public MembershipClientException(final Code code, final String message) {
        super(message);
        this.code = code;
    }

    public MembershipClientException(final Code code, final Throwable throwable) {
        super(throwable);
        this.code = code;
    }

    public MembershipClientException(final Code code, final String message, final Throwable throwable) {
        super(message, throwable);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public static enum Code {
        // 1.
        INVALID_MEMBERSHIP_CLIENT_LCM("Membership client cannot retransition to the same desired state"),
        // 2.
        MEMBERSHIP_CLIENT_INIT_FAILURE("Failed to initialize the membership client"),
        // 3.
        MEMBERSHIP_CLIENT_TINI_FAILURE("Failed to cleanly shutdown the membership client"),
        // 4.
        MEMBERSHIP_INVALID_ARG("Invalid arguments passed"),
        // 5.
        MEMBERSHIP_SERVER_DEADLINE_EXCEEDED("Membership server failed to respond within its deadline"),
        // 6.
        MEMBERSHIP_SERVER_ERROR("Membership server encountered an error"),
        // n.
        UNKNOWN_FAILURE(
                "Membership client internal failure. Check exception stacktrace for more details of the failure");

        private String description;

        private Code(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
