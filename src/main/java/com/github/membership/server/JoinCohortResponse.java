package com.github.membership.server;

public final class JoinCohortResponse {
    private Member member;

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    @Override
    public String toString() {
        return "JoinCohortResponse [member=" + member + "]";
    }

}
