package com.togethermusic.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.common.response.Response;
import com.togethermusic.user.dto.BindMusicAccountRequest;
import com.togethermusic.user.dto.KugouAccountStatusResponse;
import com.togethermusic.user.dto.KugouQrLoginCheckResponse;
import com.togethermusic.user.dto.KugouQrLoginStartResponse;
import com.togethermusic.user.dto.MusicAccountVO;
import com.togethermusic.user.dto.NeteaseAccountStatusResponse;
import com.togethermusic.user.dto.NeteaseQrLoginCheckResponse;
import com.togethermusic.user.dto.NeteaseQrLoginStartResponse;
import com.togethermusic.user.entity.UserMusicAccount;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import com.togethermusic.user.service.KugouAccountService;
import com.togethermusic.user.service.NeteaseAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户音乐平台账号管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/user/music-accounts")
@RequiredArgsConstructor
@SaCheckLogin
public class UserMusicAccountController {

    private final UserMusicAccountRepository accountRepository;
    private final NeteaseAccountService neteaseAccountService;
    private final KugouAccountService kugouAccountService;

    /**
     * 绑定音乐平台账号
     * POST /api/v1/user/music-accounts
     */
    @PostMapping
    public Response<Void> bindAccount(@Valid @RequestBody BindMusicAccountRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("[MusicAccount] User {} binding {} account", userId, request.getSource());

        // 检查是否已存在，存在则更新
        UserMusicAccount account = accountRepository
                .findByUserIdAndSource(userId, request.getSource())
                .orElse(new UserMusicAccount());

        account.setUserId(userId);
        account.setSource(request.getSource());
        account.setAuthToken(request.getAuthToken());
        account.setRefreshToken(request.getRefreshToken());
        account.setExpiresAt(request.getExpiresAt());
        account.setIsActive(true);

        accountRepository.save(account);
        log.info("[MusicAccount] User {} {} account saved", userId, request.getSource());

        return Response.success(null);
    }

    /**
     * 获取已绑定的账号列表
     * GET /api/v1/user/music-accounts
     */
    @GetMapping
    public Response<List<MusicAccountVO>> listAccounts() {
        Long userId = StpUtil.getLoginIdAsLong();

        List<UserMusicAccount> accounts = accountRepository.findByUserId(userId);

        List<MusicAccountVO> voList = accounts.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return Response.success(voList);
    }

    /**
     * 获取指定平台的绑定信息
     * GET /api/v1/user/music-accounts/{source}
     */
    @GetMapping("/{source}")
    public Response<MusicAccountVO> getAccount(@PathVariable String source) {
        Long userId = StpUtil.getLoginIdAsLong();

        return accountRepository.findByUserIdAndSource(userId, source)
                .map(account -> Response.success(toVO(account)))
                .orElse(Response.success(null));
    }

    /**
     * 解绑账号
     * DELETE /api/v1/user/music-accounts/{source}
     */
    @DeleteMapping("/{source}")
    public Response<Void> unbindAccount(@PathVariable String source) {
        Long userId = StpUtil.getLoginIdAsLong();
        log.info("[MusicAccount] User {} unbinding {} account", userId, source);

        accountRepository.deleteByUserIdAndSource(userId, source);

        return Response.success(null);
    }

    /**
     * 验证 Token 有效性
     * POST /api/v1/user/music-accounts/{source}/validate
     */
    @PostMapping("/{source}/validate")
    public Response<Boolean> validateToken(@PathVariable String source) {
        Long userId = StpUtil.getLoginIdAsLong();

        if ("netease".equalsIgnoreCase(source)) {
            boolean valid = neteaseAccountService.validateCurrentUser(userId).isValid();
            return Response.success(valid);
        }
        if ("kugou".equalsIgnoreCase(source)) {
            boolean valid = kugouAccountService.validateCurrentUser(userId).valid();
            return Response.success(valid);
        }

        boolean isValid = accountRepository.findByUserIdAndSource(userId, source)
                .map(UserMusicAccount::isValid)
                .orElse(false);

        return Response.success(isValid);
    }

    /**
     * 初始化网易云二维码登录
     * POST /api/v1/user/music-accounts/netease/qr/start
     */
    @PostMapping("/netease/qr/start")
    public Response<NeteaseQrLoginStartResponse> startNeteaseQrLogin() {
        return Response.success(neteaseAccountService.startQrLogin());
    }

    /**
     * 轮询网易云二维码登录状态
     * GET /api/v1/user/music-accounts/netease/qr/check?key=xxx
     */
    @GetMapping("/netease/qr/check")
    public Response<NeteaseQrLoginCheckResponse> checkNeteaseQrLogin(@RequestParam String key) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Response.success(neteaseAccountService.checkQrLogin(userId, key));
    }

    /**
     * 发送网易云手机验证码
     * POST /api/v1/user/music-accounts/netease/captcha/send
     */
    @PostMapping("/netease/captcha/send")
    public Response<Boolean> sendNeteaseCaptcha(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String ctcode = body.getOrDefault("ctcode", "86");
        return Response.success(neteaseAccountService.sendCaptcha(phone, ctcode));
    }

    /**
     * 网易云手机验证码登录
     * POST /api/v1/user/music-accounts/netease/captcha/login
     */
    @PostMapping("/netease/captcha/login")
    public Response<NeteaseAccountStatusResponse> loginNeteaseByCaptcha(
            @RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String phone = body.get("phone");
        String captcha = body.get("captcha");
        String ctcode = body.getOrDefault("ctcode", "86");
        return Response.success(neteaseAccountService.loginByCaptcha(userId, phone, captcha, ctcode));
    }

    /**
     * 导入网易云 Cookie
     * POST /api/v1/user/music-accounts/netease/cookie/import
     */
    @PostMapping("/netease/cookie/import")
    public Response<NeteaseAccountStatusResponse> importNeteaseCookie(
            @RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String cookie = body.get("cookie");
        String uid = body.get("uid");
        return Response.success(neteaseAccountService.importCookie(userId, cookie, uid));
    }

    /**
     * 手动刷新网易云登录态
     * POST /api/v1/user/music-accounts/netease/refresh
     */
    @PostMapping("/netease/refresh")
    public Response<NeteaseAccountStatusResponse> refreshNeteaseAccount() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Response.success(neteaseAccountService.refreshCurrentUser(userId));
    }

    /**
     * 获取网易云登录态状态
     * GET /api/v1/user/music-accounts/netease/status
     */
    @GetMapping("/netease/status")
    public Response<NeteaseAccountStatusResponse> getNeteaseAccountStatus() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Response.success(neteaseAccountService.validateCurrentUser(userId));
    }

    @PostMapping("/kugou/token/import")
    public Response<KugouAccountStatusResponse> importKugouToken(
            @RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String token = body.get("token");
        return Response.success(kugouAccountService.importToken(userId, token));
    }

    @PostMapping("/kugou/qr/start")
    public Response<KugouQrLoginStartResponse> startKugouQrLogin() {
        return Response.success(kugouAccountService.startQrLogin());
    }

    @GetMapping("/kugou/qr/check")
    public Response<KugouQrLoginCheckResponse> checkKugouQrLogin(@RequestParam String key) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Response.success(kugouAccountService.checkQrLogin(userId, key));
    }

    @PostMapping("/kugou/captcha/send")
    public Response<Boolean> sendKugouCaptcha(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        return Response.success(kugouAccountService.sendCaptcha(phone));
    }

    @PostMapping("/kugou/captcha/login")
    public Response<KugouAccountStatusResponse> loginKugouByCaptcha(
            @RequestBody Map<String, String> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        String phone = body.get("phone");
        String captcha = body.get("captcha");
        return Response.success(kugouAccountService.loginByCaptcha(userId, phone, captcha));
    }

    @PostMapping("/kugou/refresh")
    public Response<KugouAccountStatusResponse> refreshKugouAccount() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Response.success(kugouAccountService.refreshCurrentUser(userId));
    }

    @GetMapping("/kugou/status")
    public Response<KugouAccountStatusResponse> getKugouAccountStatus() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Response.success(kugouAccountService.validateCurrentUser(userId));
    }

    private MusicAccountVO toVO(UserMusicAccount account) {
        return MusicAccountVO.builder()
                .id(account.getId())
                .source(account.getSource())
                .isActive(account.getIsActive())
                .isExpired(account.isTokenExpired())
                .expiresAt(account.getExpiresAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
