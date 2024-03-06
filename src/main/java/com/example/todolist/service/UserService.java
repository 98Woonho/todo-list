package com.example.todolist.service;

import com.example.todolist.domain.dto.EmailAuthDto;
import com.example.todolist.domain.entity.EmailAuth;
import com.example.todolist.domain.repository.EmailAuthRepository;
import com.example.todolist.domain.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailAuthRepository emailAuthRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Transactional(rollbackFor = Exception.class)
    public String sendEmail(EmailAuthDto emailAuthDto) throws NoSuchAlgorithmException, MessagingException {
        if(userRepository.existsById(emailAuthDto.getEmail())) {
            return "FAILURE_DUPLICATE_EMAIL";
        }

        String code = RandomStringUtils.randomNumeric(6);
        emailAuthDto.setCode(code);
        emailAuthDto.setCreatedAt(new Date());
        emailAuthDto.setExpiresAt(DateUtils.addMinutes(emailAuthDto.getCreatedAt(), 5)); // 이메일을 보내고 난 뒤 5분 후의 시간
        emailAuthDto.setVerified(false);

        String saltInput = String.format("%s%s%f%f",
                emailAuthDto.getEmail(),
                code,
                Math.random(),
                Math.random());

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.reset();
        md.update(saltInput.getBytes(StandardCharsets.UTF_8));

        String salt = String.format("%0128x", new BigInteger(1, md.digest()));

        emailAuthDto.setSalt(salt);

        Context context = new Context();
        context.setVariable("emailAuthDto", emailAuthDto);

        String textHtml = templateEngine.process("user/joinEmail.html", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper mimemessageHelper = new MimeMessageHelper(message, false);
        mimemessageHelper.setTo(emailAuthDto.getEmail());
        mimemessageHelper.setSubject("[Coremap] 회원가입 인증번호");
        mimemessageHelper.setText(textHtml, true);
        mailSender.send(message);

        EmailAuth emailAuth = EmailAuthDto.emailAuthDtoToEntity(emailAuthDto);

        emailAuthRepository.save(emailAuth);

        return "SUCCESS";
    }

    @Transactional(rollbackFor = Exception.class)
    public String verifyCode(EmailAuthDto emailAuthDto) {
        EmailAuth emailAuth = emailAuthRepository.findByEmailAndCodeAndSalt(emailAuthDto.getEmail(), emailAuthDto.getCode(), emailAuthDto.getSalt());

        if (emailAuth == null) {
            return "FAILURE_INVALID_CODE";
        }

        if (new Date().compareTo(emailAuth.getExpiresAt()) > 0) {
            return "FAILURE_EXPIRED";
        }

        emailAuth.setIsVerified(true);

        emailAuthRepository.save(emailAuth);

        return "SUCCESS";
    }
}
