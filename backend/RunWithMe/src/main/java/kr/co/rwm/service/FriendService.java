package kr.co.rwm.service;

import java.util.List;
import java.util.Map;

import kr.co.rwm.entity.Friend;
import kr.co.rwm.entity.User;

public interface FriendService {
	List<User> list(int uid);

	Friend insert(int uid, int friendId);

	Long delete(int uid, int friendId);

	List<User> match(int uid, String gender);

	boolean findByUserIdAndFriendId(int uid, int friendId);

	List<User> onlineList(int uid);

	List<User> offlineList(int uid);
}
