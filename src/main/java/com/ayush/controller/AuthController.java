package com.ayush.controller;

import com.ayush.config.JwtProvider;
import com.ayush.model.TwoFactorOTP;
import com.ayush.model.User;
import com.ayush.repository.UserRepository;
import com.ayush.response.AuthResponse;
import com.ayush.service.CustomUserDetailsService;
import com.ayush.service.EmailService;
import com.ayush.service.TwoFactorOtpService;
import com.ayush.utils.OtpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private TwoFactorOtpService twoFactorOtpService;

    @Autowired
    private EmailService emailService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> register(@RequestBody User user) throws Exception {



        User isEmailExists = userRepository.findByEmail(user.getEmail());
        if(isEmailExists != null) {
            throw new Exception("email is already in use with another account");
        }

        User newUser  = new User();
        newUser.setEmail(user.getEmail());
        newUser.setPassword(user.getPassword());
        newUser.setFullName(user.getFullName());



        User savedUser = userRepository.save(newUser);

        Authentication auth=new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                user.getPassword()
        );

        SecurityContextHolder.getContext().setAuthentication(auth);

        String jwt= JwtProvider.generateToken(auth);

        AuthResponse res=new AuthResponse();
        res.setJwt(jwt);
        res.setStatus(true);
        res.setMessage("register success");


        return new ResponseEntity<>(res, HttpStatus.CREATED);



    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> login(@RequestBody User user) throws Exception {

        String userName=user.getEmail();
        String password=user.getPassword();




        Authentication auth=authenticate(userName, password);

        SecurityContextHolder.getContext().setAuthentication(auth);

        String jwt= JwtProvider.generateToken(auth);


        User authUser =userRepository.findByEmail(userName);



        if (user.getTwoFactorAuth().isEnabled()) {
            AuthResponse res = new AuthResponse();
            res.setMessage("Two factor auth is enabled");
            res.setTwoFactorAuthEnabled(true);
            String otp= OtpUtils.generateOTP();

            TwoFactorOTP oldTwoFactorOTP =twoFactorOtpService.findByUser(authUser.getId());
            if (oldTwoFactorOTP != null) {
                twoFactorOtpService.deleteTwoFactorOtp(oldTwoFactorOTP);
            }

            TwoFactorOTP newTwoFactorOTP = twoFactorOtpService.createTwoFactorOtp(authUser,otp,jwt);

            emailService.sendVerificationOtpEmail(userName, otp);



            res.setSession(newTwoFactorOTP.getId());
            return new ResponseEntity<>(res, HttpStatus.ACCEPTED);
        }



        AuthResponse res=new AuthResponse();
        res.setJwt(jwt);
        res.setStatus(true);
        res.setMessage("login successful");


        return new ResponseEntity<>(res, HttpStatus.CREATED);


    }

    private Authentication authenticate(String userName, String password)  {
         UserDetails userDetails = customUserDetailsService.loadUserByUsername(userName);

         if (userDetails==null) {
             throw new BadCredentialsException("Invalid username");
         }

         if (!password.equals(userDetails.getPassword())) {
             throw new BadCredentialsException("Invalid  password");

         }
         return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
    }

    public ResponseEntity<AuthResponse> verifySigninOtp(
            @PathVariable String otp,
            @RequestParam String id) throws Exception {
        TwoFactorOTP twoFactorOTP =twoFactorOtpService.findById(id);

        if (twoFactorOtpService.verifyTwoFactorOtp(twoFactorOTP,otp)) {
             AuthResponse res = new AuthResponse();
             res.setMessage("TwoFactor authentication verified");
             res.setTwoFactorAuthEnabled(true);
             res.setJwt(twoFactorOTP.getJwt());
             return new ResponseEntity<>(res, HttpStatus.OK);
        }
        throw new Exception("Invalid otp");
    }
}
