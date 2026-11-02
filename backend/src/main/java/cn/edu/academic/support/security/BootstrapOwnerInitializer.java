package cn.edu.academic.support.security;

import cn.edu.academic.support.user.mapper.UserAccountMapper;
import cn.edu.academic.support.user.model.UserAccount;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class BootstrapOwnerInitializer implements ApplicationRunner {
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final String login;
    private final String password;
    private final String displayName;

    public BootstrapOwnerInitializer(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder,
                                     @Value("${app.bootstrap.owner-login:}") String login,
                                     @Value("${app.bootstrap.owner-password:}") String password,
                                     @Value("${app.bootstrap.owner-display-name:系统负责人}") String displayName) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.login = login;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(login) && !StringUtils.hasText(password)) return;
        if (!StringUtils.hasText(login) || !StringUtils.hasText(password) || password.length() < 10) {
            throw new IllegalStateException("BOOTSTRAP_OWNER_LOGIN 与至少 10 位的 BOOTSTRAP_OWNER_PASSWORD 必须同时配置");
        }
        Long count = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getAccountType, "SYSTEM_OWNER"));
        if (count > 0) return;
        UserAccount owner = new UserAccount();
        owner.setLoginName(login.trim());
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setAccountType("SYSTEM_OWNER");
        owner.setDisplayName(displayName.trim());
        owner.setAccountStatus("ACTIVE");
        owner.setTokenVersion(0);
        userAccountMapper.insert(owner);
    }
}
