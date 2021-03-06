package kr.co.rwm.controller;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.annotations.ApiOperation;
import kr.co.rwm.dto.RunningUserDto;
import kr.co.rwm.dto.UserDto;
import kr.co.rwm.entity.Gugun;
import kr.co.rwm.entity.RunningUser;
import kr.co.rwm.entity.User;
import kr.co.rwm.model.Response;
import kr.co.rwm.model.ResponseMessage;
import kr.co.rwm.model.RestException;
import kr.co.rwm.model.StatusCode;
import kr.co.rwm.service.AreaService;
import kr.co.rwm.service.ChallengeService;
import kr.co.rwm.service.JwtTokenProvider;
import kr.co.rwm.service.RanksService;
import kr.co.rwm.service.RecordService;
import kr.co.rwm.service.S3Service;
import kr.co.rwm.service.UserService;
import lombok.RequiredArgsConstructor;

/*
 * UserController
 * <pre>
 * <b> History:</b>
 *			김형텍, ver.0.4 , 2020-11-12 : Remove usage of Generic wildcard type
 * </pre>
 * 
 * @author 김형택
 * @version 0.4, 2020-10-26, 유저 관리 Controller
 * @see None
 * 
 */
@CrossOrigin(origins="*")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
	
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RedisTemplate<String, String> redis;
	private final S3Service s3Service;
	private final AreaService areaService;
	private final UserService userService;
	private final RanksService rankService;
	private final RecordService recordService;
	private final ChallengeService challengeService;
	
	/**
	 * 회원가입 - 이메일 중복 여부 True/False를 판단하고, True일 경우 JSON 객체 기반으로 회원가입을 진행한다.
	 * 
	 * @param	user userName, userEmail, userPw <br>
	 * 			
	 * @return ResponseEntity<Response<Object>> - StatusCode,
	 *         ResponseMessage(SIGNUP_SUCCESS), HttpStatus <br>
	 * @apiNote User user - 
	 * 			String userEmail, String userPw, String userName, String profile, boolean emailAuth <br>        
	 *      
	 * @exception RestException EMAIL_CHECK_FAIL
	 */
	@ApiOperation(value = "회원 가입", response = ResponseEntity.class, notes = "userName, userEmail, userPw가 담긴 JSON객체와 MultipartFile의 프로필 이미지로 회원가입을 한다.")
	@PostMapping("")
	public ResponseEntity<Response<Object>> signup(@RequestBody UserDto user, MultipartFile profile){
		if(!user.getAuth()) {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN,ResponseMessage.EMAIL_CHECK_FAIL,false),HttpStatus.FORBIDDEN);
		}else {
			Gugun gugun = areaService.findGugunByGugunId(user.getGugunId().getGugunId());
			user.setGugunId(gugun);
			User result = userService.join(user, passwordEncoder.encode(user.getPassword()));
			rankService.join(result);
			recordService.join(result);
			
			return new ResponseEntity<>(new Response<>(StatusCode.CREATED,ResponseMessage.SIGNUP_SUCCESS),HttpStatus.CREATED);
		}
	}
	
	/**
	 * 이메일 중복 확인 
	 * 
	 * @param  userEmail 사용자 이메일
	 * @return ResponseEntity<Response<Object>> - StatusCode,
	 *         ResponseMessage(ALREADY_USER_EMAIL, EMAIL_CHECK_OK), HttpStatus <br>
	 * @apiNote 중복일 경우 False / 중복이 아닐 경우 True를 반환        
	 *      
	 */
	@ApiOperation(value = "이메일 중복 확인", response = ResponseEntity.class, notes = "userEmail로 이메일 중복체크를 한다.")
	@GetMapping("/check/{userEmail}")
	public ResponseEntity<Response<Object>> emailCheck(@PathVariable String userEmail){
		if(userService.findByUserEmail(userEmail).isPresent()) {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN,ResponseMessage.ALREADY_USER_EMAIL,false),HttpStatus.FORBIDDEN);
		}
		return new ResponseEntity<>(new Response<>(StatusCode.OK,ResponseMessage.EMAIL_CHECK_OK,true),HttpStatus.OK);
	}
	
	/**
	 * 로그인
	 * 
	 * @param  user 사용자 정보 (userEmail, userPw)
	 * @return ResponseEntity<Response<Object>> - StatusCode, member
	 *         ResponseMessage(USER_NOT_FOUND, SIGNIN_FAIL, SIGNIN_SUCCESS), HttpStatus <br>
	 * @apiNote 해당 사용자 정보가 없는 경우 : USER_NOT_FOUND <br>
	 * 			비밀번호가 일치하지 않는 경우 : SIGNIN_FAIL <br>
	 * 			로그인 성공한 경우 : SIGNIN_SUCCESS <br>
	 *      
	 */
	@ApiOperation(value = "로그인", response = ResponseEntity.class, notes = "userEmail, userPw로 로그인한다.")
	@PostMapping("/signin")
	public ResponseEntity<Response<Object>> signin(@RequestBody UserDto user, HttpServletResponse response){
		User member = userService.findByUserEmail(user.getUserEmail())
				.orElse(null);
		if(member==null) {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN,ResponseMessage.USER_NOT_FOUND),HttpStatus.FORBIDDEN);
		}
		if(!passwordEncoder.matches(user.getPassword(),member.getPassword())) {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.SIGNIN_FAIL),
					HttpStatus.FORBIDDEN);
		}
		String token = jwtTokenProvider.generateToken(member.getUserId(), member.getUserEmail(), member.getRoles());
		response.setHeader("AUTH", token);
		
		RunningUserDto runningUser = recordService.findRunningUserByUserId(member.getUserId());
		
		// 로그인된 사용자 목록
		redis.opsForValue().set(member.getUserId().toString(), "success");
		return new ResponseEntity<>(new Response<>(StatusCode.OK, ResponseMessage.SIGNIN_SUCCESS, runningUser),
				HttpStatus.OK);
	}
	
	/**
	 * 로그아웃 - 토큰을 만료시키고 redis에 저장하여 블랙리스트 생성(토큰만료시간까지 저장시켜두고 추후 자동 삭제)
	 * 
	 * @param
	 * @return ResponseEntity<Response<Object>> - StatusCode,
	 *         ResponseMessage(LOGOUT_SUCCESS,LOGOUT_FAIL), HttpStatus
	 * @exception FORBIDDEN
	 */
	@ApiOperation(value = "로그아웃", response = ResponseEntity.class, notes = "토큰을 만료시키고 redis에 저장하여 블랙리스트를 생성합니다.(토큰만료시간까지 저장시켜두고 추후 자동 삭제)")
	@GetMapping(path = "/signout")
	public ResponseEntity<Response<Object>> logout(HttpServletRequest request) {
		String token = request.getHeader("AUTH");
		if (jwtTokenProvider.validateToken(token)) {
			Date expirationDate = jwtTokenProvider.getExpirationDate(token);
			redis.opsForValue().set(token, "logout", expirationDate.getTime() - System.currentTimeMillis(),
					TimeUnit.MILLISECONDS);
			String userId = jwtTokenProvider.getUserPk(token);
			redis.opsForHash().delete(userId.toString());
			
			return new ResponseEntity<>(new Response<>(StatusCode.NO_CONTENT, ResponseMessage.LOGOUT_SUCCESS),
					HttpStatus.OK);
		} else {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.LOGOUT_FAIL),
					HttpStatus.FORBIDDEN);
		}
	}
	
	// 회원 정보 조회 (다른 사람)
	@GetMapping(path="/{userId}")
	public ResponseEntity<Response<Object>> userInfo(@PathVariable int userId) {
		RunningUserDto member = recordService.findRunningUserByUserId(userId);
		if(member == null) {
			return new ResponseEntity<>(new Response<>(StatusCode.NO_CONTENT, ResponseMessage.USERINFO_SEARCH_FAIL),
					HttpStatus.OK);
		}
		return new ResponseEntity<>(new Response<>(StatusCode.OK,ResponseMessage.USERINFO_SEARCH_SUCCESS, member),HttpStatus.OK);
	}
	
	// 회원 정보 조회 (해당 사용자)
	@GetMapping(path="")
	public ResponseEntity<Response<Object>> myUserInfo(HttpServletRequest request) {
		String token = request.getHeader("AUTH");
		if(jwtTokenProvider.validateToken(token)) {
			int userId = jwtTokenProvider.getUserIdFromJwt(token);
			
			// 토큰 유효성 검사를 걸쳤기 때문에 무조건 정보가 존재한다.
			RunningUserDto member = recordService.findRunningUserByUserId(userId);
			return new ResponseEntity<>(new Response<>(StatusCode.OK,ResponseMessage.USERINFO_SEARCH_SUCCESS, member),HttpStatus.OK);
		}else {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.UNAUTHORIZED),
					HttpStatus.FORBIDDEN);
		}
	}
	
	// 회원 탈퇴 유효성 검사
	@PostMapping(path="/checkPw")
	public ResponseEntity<Response<Object>> deleteCheckUser(@RequestBody UserDto user, HttpServletRequest request) {
		String token = request.getHeader("AUTH");
		if(jwtTokenProvider.validateToken(token)) {
			String userEmail = jwtTokenProvider.getUserEmailFromJwt(token);
			Optional<User> userOp = userService.findByUserEmail(userEmail);
			if(!userOp.isPresent()) {
				return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.USER_NOT_FOUND),
						HttpStatus.FORBIDDEN);
			}
			String pw = userOp.get().getPassword();
			if (!passwordEncoder.matches(user.getPassword(), pw)) {
				return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.USER_DELETE_FAIL,false),
						HttpStatus.FORBIDDEN);
			}
			return new ResponseEntity<>(new Response<>(StatusCode.NO_CONTENT,ResponseMessage.USER_DELETE_SUCCESS,true),HttpStatus.OK);
			
		}else {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.UNAUTHORIZED,false),
					HttpStatus.FORBIDDEN);
		}
	}
	
	// 회원 탈퇴
	@DeleteMapping(path="")
	public ResponseEntity<Response<Object>> deleteUser(HttpServletRequest request) {
		String token = request.getHeader("AUTH");
		if(jwtTokenProvider.validateToken(token)) {
			String userEmail = jwtTokenProvider.getUserEmailFromJwt(token);
			challengeService.deleteAllChallengeUserByUserEmail(userEmail);
			userService.delete(userEmail);
			return new ResponseEntity<>(new Response<>(StatusCode.NO_CONTENT,ResponseMessage.USER_DELETE_SUCCESS),HttpStatus.OK);
			
		}else {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.UNAUTHORIZED),
					HttpStatus.FORBIDDEN);
		}
	}
	
	// 회원 정보 수정
	// 기존 비밀번호를 입력받고 회원정보 수정 진행
	@PutMapping(path="")
	public ResponseEntity<Response<Object>> updateUser(@RequestBody UserDto user, HttpServletRequest request) {
		String token = request.getHeader("AUTH");
		if(jwtTokenProvider.validateToken(token)) {
			String userEmail = jwtTokenProvider.getUserEmailFromJwt(token);
			// 해당 사용자 정보
			Optional<User> member = userService.findByUserEmail(userEmail);
			if(!member.isPresent()) {
				return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.USER_NOT_FOUND),
						HttpStatus.FORBIDDEN);
			}
			if (!passwordEncoder.matches(user.getPassword(), member.get().getPassword())) {
				return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.USER_UPDATE_FAIL),
						HttpStatus.FORBIDDEN);
			}
			user.setUserId(member.get().getUserId());
			user.setUserEmail(member.get().getUserEmail());
			user.setChangePw(passwordEncoder.encode(user.getChangePw()));
			Gugun gugun = areaService.findGugunByGugunId(user.getGugunId().getGugunId());
			user.setGugunId(gugun);
			userService.update(member,user);
			return new ResponseEntity<>(new Response<>(StatusCode.OK,ResponseMessage.USER_UPDATE_SUCCESS,user),HttpStatus.OK);
			
		}else {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.UNAUTHORIZED),
					HttpStatus.FORBIDDEN);
		}
	}
	
	// 프로필 이미지 등록/수정 (이미지 사이즈 조정)
	@PutMapping("/{userId}/profile")
	public ResponseEntity<Response<Object>> uploadProfile(@PathVariable int userId, MultipartFile profile,HttpServletRequest request) {
		String token = request.getHeader("AUTH");
		if(jwtTokenProvider.validateToken(token)) {
			String url = s3Service.thumbnailUpload(profile);
			Optional<User> member = userService.findByUserId(userId);
			if(!member.isPresent()) {
				return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.USER_NOT_FOUND),
						HttpStatus.FORBIDDEN);
			}else {
				User changeUser = member.get();
				changeUser.setProfile(url);
				changeUser.setChangePw(changeUser.getPassword());
				userService.profileUpdate(member, changeUser);
				return new ResponseEntity<>(new Response<>(StatusCode.OK, ResponseMessage.UPLOAD_PROFILE_SUCCESS,changeUser),
						HttpStatus.OK);
			}
		}else {
			return new ResponseEntity<>(new Response<>(StatusCode.FORBIDDEN, ResponseMessage.UNAUTHORIZED),
					HttpStatus.FORBIDDEN);
		}
	}
	
	
}
