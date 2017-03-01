package net.kodehawa.mantarobot.commands.currency;

import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.User;
import net.kodehawa.mantarobot.utils.Expirator;

import java.util.ArrayList;
import java.util.List;

/**
 * Made by @AdrianTodt
 */
public class RateLimiter {
	private static final Expirator EXPIRATOR = new Expirator();
	private final int timeout;
	private final List<String> usersRateLimited = new ArrayList<>();

	public RateLimiter(int timeout) {
		this.timeout = timeout;
	}

	public boolean process(String userId) {
		if (usersRateLimited.contains(userId)) return false;
		usersRateLimited.add(userId);
		EXPIRATOR.letExpire(System.currentTimeMillis() + timeout, () -> usersRateLimited.remove(userId));
		return true;
	}

	public boolean process(User user) {
		return process(user.getId());
	}

	public boolean process(Member member) {
		return process(member.getUser());
	}
}
