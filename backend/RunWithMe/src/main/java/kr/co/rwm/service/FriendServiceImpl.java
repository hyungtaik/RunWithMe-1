package kr.co.rwm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import kr.co.rwm.entity.Friend;
import kr.co.rwm.entity.Ranks;
import kr.co.rwm.entity.User;
import kr.co.rwm.repo.FriendRepository;
import kr.co.rwm.repo.RanksRepository;
import kr.co.rwm.repo.UserRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class FriendServiceImpl implements FriendService {

	@Autowired
	FriendRepository friendRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	RanksRepository rankRepository;

	private final RedisTemplate<String, String> redis;

	@Override
	public List<User> list(int uid) {
		List<Friend> list = friendRepository.findByUserId(uid);
		List<User> contactsList = new ArrayList<User>();
		for (Friend friend : list) {
			contactsList.add(friend.getUser());
		}

		return contactsList;
	}

	@Override
	public List<Ranks> contactList(int uid) {
		List<Friend> list = friendRepository.findByUserId(uid);
		List<Ranks> contactsList = new ArrayList<Ranks>();
		Optional<Ranks> rank = null;
		for (Friend friend : list) {
			rank = rankRepository.findByUserId(friend.getUser());
			if (rank.isPresent()) {
				contactsList.add(rank.get());
			} else {
				return null;
			}
		}

		return contactsList;
	}

	@Override
	public List<User> onlineList(int uid) {
		List<Friend> list = friendRepository.findByUserId(uid);
		List<User> contactsList = new ArrayList<User>();
		for (Friend friend : list) {
			if (redis.opsForValue().get(friend.getUser().getUserId().toString()) != null) {
				contactsList.add(friend.getUser());
			}
		}
		return contactsList;
	}

	@Override
	public List<User> offlineList(int uid) {
		List<Friend> list = friendRepository.findByUserId(uid);
		List<User> contactsList = new ArrayList<User>();
		for (Friend friend : list) {
			if (redis.opsForValue().get(friend.getUser().getUserId().toString()) == null) {
				contactsList.add(friend.getUser());
			}
		}
		return contactsList;
	}

	@Override
	public List<User> match(int uid, String gender) {
		Optional<User> users = userRepository.findByUserId(uid);
		if (users.isPresent()) {
			User user = users.get();
			int dong = user.getGugunId().getGugunId();
			int sex;
			if (gender.equals("male"))
				sex = 1;
			else
				sex = 2;
			Optional<Ranks> ranking = rankRepository.findByUserId(user);
			if (ranking.isPresent()) {
				int tier = ranking.get().getTier();
				List<Ranks> userList = rankRepository.findByTier(tier);
				List<User> result = new ArrayList<User>();
				for (Ranks ranks : userList) {
					if (ranks.getUserId().getGender() == sex && ranks.getUserId().getGugunId().getGugunId() == dong
							&& ranks.getUserId().getUserId() != uid)
						result.add(ranks.getUserId());
				}
				return result;
			} else {
				return null;
			}

		} else {
			return null;
		}

	}

	public List<User> analysis(int uid, String gender) {
		Optional<User> users = userRepository.findByUserId(uid);
		if (users.isPresent()) {
			User user = users.get();
			int sex;
			if (gender.equals("male"))
				sex = 1;
			else
				sex = 2;
			Optional<Ranks> ranking = rankRepository.findByUserId(user);
			if (ranking.isPresent()) {
				int tier = ranking.get().getTier();
				List<Ranks> userList = rankRepository.findByTier(tier);
				List<User> result = new ArrayList<User>();
				for (Ranks ranks : userList) {
					if (ranks.getUserId().getGender() == sex && ranks.getUserId().getUserId() != uid)
						result.add(ranks.getUserId());
				}

				return result;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	@Override
	public Friend insert(int uid, int friendId) {

		Friend relation = Friend.builder().userId(uid).build();

		User user = userRepository.findByUserId(friendId).orElse(null);
		if (user == null)
			return null;

		relation.setUser(user);
		Friend result = friendRepository.save(relation);

		return result;
	}

	@Override
	@Transactional
	public Long delete(int uid, int friendId) {
		Optional<User> friend = userRepository.findByUserId(friendId);
		if (!friend.isPresent())
			return -1L;

		Long ret = friendRepository.deleteByUserIdAndUserUserId(uid, friendId);
		return ret;
	}

	@Override
	public boolean findByUserIdAndFriendId(int uid, int friendId) {
		Optional<Friend> friend = friendRepository.findByUserIdAndUserUserId(uid, friendId);
		if (friend.isPresent())
			return true;
		else
			return false;
	}

}
