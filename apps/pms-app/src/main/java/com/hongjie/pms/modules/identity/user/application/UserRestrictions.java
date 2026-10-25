package com.hongjie.pms.modules.identity.user.application;

import com.hongjie.pms.modules.identity.user.infrastructure.persistence.entity.User;
import java.time.LocalDateTime;
import java.util.stream.Stream;

/** Both legacy deadline columns represent the same signup restriction; never release the longer one early. */
public final class UserRestrictions {
    private UserRestrictions() {}
    public static LocalDateTime signupUntil(User user) {
        return Stream.of(user.getBanSignupEndTime(), user.getPunishmentEndTime()).filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
    }
    public static boolean signupBlocked(User user, LocalDateTime now) {
        LocalDateTime until = signupUntil(user);
        return Integer.valueOf(1).equals(user.getIsBannedSignup()) && (until == null || until.isAfter(now));
    }
}
