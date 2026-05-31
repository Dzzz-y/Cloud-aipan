package net.aipan.dcloud_aipan.aspect;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.aipan.dcloud_aipan.annotation.ShareCodeCheck;
import net.aipan.dcloud_aipan.enums.BizCodeEnum;
import net.aipan.dcloud_aipan.exception.BizException;
import net.aipan.dcloud_aipan.util.JwtUtil;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
public class ShareCodeAspect {

    private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /**
     * 设置当前线程共享Id
     * @param shareId
     */
    public static void set(Long shareId) {
        threadLocal.set(shareId);
    }

    /**
     * 获取当前线程共享Id
     */
    public static Long get() {
        return threadLocal.get();
    }

    @Pointcut("@annotation(shareCodeCheck)")
    public void pointCutShareCodeCheck(ShareCodeCheck shareCodeCheck) {
    }

    /**
     * 配置环绕通知,围绕方法执行
     */
    @Around("pointCutShareCodeCheck(shareCodeCheck)")
    public Object around(ProceedingJoinPoint joinPoint, ShareCodeCheck shareCodeCheck) throws Throwable{
        HttpServletRequest request = ((ServletRequestAttributes) (RequestContextHolder.getRequestAttributes())).getRequest();
        String shareToken = request.getHeader("share-token");
        if (StringUtils.isBlank(shareToken)) {
            throw new BizException(BizCodeEnum.SHARE_CODE_ILLEGAL);
        }
        Claims claims = JwtUtil.checkShareJWT(shareToken);
        if (claims == null) {
            log.error("share-token 解析失败");
            throw new BizException(BizCodeEnum.SHARE_CODE_ILLEGAL);
        }
        Long shareId = Long.valueOf(claims.get(JwtUtil.CLAIM_SHARE_KEY) + "");
        set(shareId);
        log.info("环绕通知执行前:");
        try {
            Object obj = joinPoint.proceed();
            log.info("环绕通知执行后:");
            return obj;
        } finally {
            threadLocal.remove();
        }
    }
}
